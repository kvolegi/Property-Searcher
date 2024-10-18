# Архітектура системи: Property Searcher 3 🏛️

> **Рівень документа:** Software Architecture Document (SAD)  
> **Методологія:** C4 Model (Context, Container, Component, Code) & Clean Architecture  
> **Головний архітектор:** `kvolegi`

---

## 1. C4 Контекстна діаграма системи (System Context)

```mermaid
flowchart TD
    User["📱 Рієлтор / Кінцевий користувач"]
    
    subgraph PlatformBoundary ["Property Searcher 3 Enterprise Platform"]
        DesktopApp["🖥️ Core Desktop Client\n(Java 21 LTS, Swing, i18n, Ingestion Engine)"]
        BackendServer["☁️ Enterprise Backend Service\n(Spring Boot 3.3.4, JPA, REST, Actuator)"]
    end

    subgraph ExternalSources ["Зовнішні провайдери та платформи"]
        Portal1["🌐 OLX.ua (Akamai CDN Edge Nodes)"]
        Portal2["🌐 DOM.RIA.com (Cloudflare CDN / API)"]
        NBU["🏦 Національний банк України (Exchange Rates API)"]
        Telegram["📬 Telegram Bot API Gateway"]
    end

    User <-->|Візуальне керування фільтрами та мовою| DesktopApp
    User <-->|Отримання фотоальбомів та команди| Telegram
    DesktopApp -->|Низьколатентне опитування 1.8с| Portal1
    DesktopApp -->|Скрапінг перевірених пропозицій| Portal2
    DesktopApp -->|Запит офіційного курсу раз на добу| NBU
    DesktopApp -->|Асинхронний SendMediaGroup| Telegram
    BackendServer -->|REST API / OpenAPI 3.0| User
    BackendServer -.->|Синхронізація лістингів та цінової історії| DesktopApp
```

---

## 2. C4 Діаграма контейнерів (Container Diagram)

```mermaid
flowchart LR
    subgraph ClientContainer ["Desktop Client Container (Zero-Cloud Native)"]
        GUI["BotGUI\n(Bilingual UA/EN, FlatLaf)"]
        Engine["ParserOLX\n(DOM/HTML Ingestion Engine)"]
        Scheduler["Adaptive Rate Scheduler\n(Token-Bucket & Staggering)"]
        Buffer["Circular Memory Buffer\n(50 Slots, O(1) Check)"]
        CurrServ["CurrencyService\n(3-Tier NBU Gateway + Cache)"]
        AsyncDisp["notificationExecutor\n(Cached Worker Pool)"]
    end

    subgraph BackendContainer ["Enterprise Cloud Container (Spring Boot 3)"]
        Controller["PropertyController\n(REST API & Swagger UI)"]
        TrackingService["TrackingService\n(Business Workflow Engine)"]
        Analytics["PriceAnalyticsService\n(Anomaly & Volatility Math)"]
        Repo["Spring Data Repositories\n(Hibernate / JPA)"]
        Database[("PostgreSQL 16\n(Relational Storage)")]
    end

    GUI --> Engine
    Engine --> Scheduler
    Scheduler --> Buffer
    Buffer --> CurrServ
    CurrServ --> AsyncDisp
    AsyncDisp --> Telegram["Telegram API"]

    Controller --> TrackingService
    TrackingService --> Analytics
    TrackingService --> Repo
    Repo --> Database
```

---

## 3. C4 Діаграма компонентів бекенду (Backend Component Diagram)

```mermaid
flowchart TD
    subgraph API_Layer ["API & Interface Layer"]
        PC["PropertyController\n(@RestController)"]
        TB["PropertyTelegramBot\n(@Component TelegramLongPollingBot)"]
        Actuator["Spring Boot Actuator\n(/health, /metrics)"]
    end

    subgraph Core_Services ["Service & Domain Logic"]
        TS["TrackingService\n(Координатор оновлень)"]
        PAS["PriceAnalyticsService\n(Аналітика волатильності)"]
        SS["ScraperService\n(Фасад скрапінгу)"]
        TNS["TelegramNotificationService\n(Асинхронний диспетчер)"]
    end

    subgraph Strategy_Layer ["Strategy Pattern Layer"]
        OLX_Strat["OlxScraperStrategy"]
        DOMRIA_Strat["DomRiaScraperStrategy"]
        MOCK_Strat["MockScraperStrategy"]
    end

    subgraph Persistence_Layer ["Data Access Layer (Spring Data JPA)"]
        UR["UserRepository"]
        STR["SearchTargetRepository"]
        DPR["DiscoveredPropertyRepository"]
        PHR["PriceHistoryRepository"]
    end

    PC --> TS
    TB --> TS
    TS --> SS
    TS --> PAS
    TS --> TNS
    SS --> OLX_Strat
    SS --> DOMRIA_Strat
    SS --> MOCK_Strat
    TS --> UR
    TS --> STR
    TS --> DPR
    TS --> PHR
```

---

## 4. Життєвий цикл лістингу (Listing State Machine)

```mermaid
stateDiagram-v2
    [*] --> DISCOVERED: Оголошення з'явилося на CDN ноді порталу
    DISCOVERED --> SILENT_BASELINE: Перший запуск системи (30 перших оголошень)
    SILENT_BASELINE --> BUFFERED: Запис ID у хеш-сет без нотифікації
    
    DISCOVERED --> DUPLICATE_CHECK: Звичайний робочий цикл
    DUPLICATE_CHECK --> BUFFERED: ID знайдено у 50-слотовому буфері (Пропуск)
    
    DUPLICATE_CHECK --> EXTRACTION: Нове унікальне оголошення
    EXTRACTION --> MEDIA_COMPOSED: Парсинг DOM (галерея до 10 фото, ціна, гео)
    MEDIA_COMPOSED --> CURRENCY_CONVERTED: DUAL-конвертація за курсом НБУ
    CURRENCY_CONVERTED --> NIGHT_MODE_CHECK: Перевірка часу (23:00-07:00 Kyiv)
    
    NIGHT_MODE_CHECK --> DISPATCHED_SILENT: Ніч (disable_notification = true)
    NIGHT_MODE_CHECK --> DISPATCHED_NORMAL: День (Звичайний звуковий пуш)
    
    DISPATCHED_SILENT --> PERSISTED: Збереження у БД / Оновлення буфера
    DISPATCHED_NORMAL --> PERSISTED: Збереження у БД / Оновлення буфера
    PERSISTED --> [*]
```

---

## 5. Event-Driven Sequence Diagram (Діаграма послідовності)

```mermaid
sequenceDiagram
    autonumber
    actor Realtor as Рієлтор
    participant GUI as Desktop GUI / BotTG
    participant NBU as NBU Currency API
    participant Cache as currency_cache.json
    participant Worker as Monitoring Thread (Worker)
    participant CDN as OLX / DOM.RIA CDN Nodes
    participant Async as notificationExecutor Pool
    participant TG as Telegram Bot API

    Realtor->>GUI: Старт моніторингу
    GUI->>Cache: Перевірка валідності кешу курсів (TTL 24h)
    alt Кеш застарів або відсутній
        GUI->>NBU: Фоновий HTTP GET (Timeout 3000ms)
        NBU-->>Cache: Запис USD/EUR курсів у JSON
    end
    GUI->>Worker: Ініціалізація N потоків з фазовим зсувом (Phase Staggering)
    Worker->>CDN: HTTP GET (User-Agent Spoofer + Jitter ±150ms)
    CDN-->>Worker: HTML корисне навантаження
    Worker->>Worker: 50-Slot Circular Buffer: перевірка наявності ID за O(1)
    alt Новий лістинг виявлено
        Worker->>Worker: Adaptive Burst Check (+500ms повторна перевірка)
        Worker->>Async: Делегування відправки у worker pool
        Note over Worker: Основний моніторинг продовжується без затримок!
        Async->>Async: Розрахунок ціни DUAL: $65 000 (≈ 2 704 000 грн)
        Async->>Async: Перевірка Night Silent Mode (23:00 - 07:00)
        Async->>TG: SendMediaGroup (до 10 фотоальбомних карток)
        TG-->>Realtor: Миттєве push-сповіщення у Telegram
    end
```

---

## 6. Каталог використаних шаблонів проєктування (Design Patterns)

| Шаблон (GoF Pattern) | Клас / Компонент | Роль в системі |
| :--- | :--- | :--- |
| **Strategy** | `ScraperStrategy`, `OlxScraperStrategy` | Ізоляція алгоритмів парсингу для різних порталів |
| **Token-Bucket Rate Limiter** | `BotTG.calculateAdaptiveDelay()` | Запобігання блокуванням за IP та WAF CloudFront |
| **Circuit Breaker / Fallback** | `CurrencyService` | 3-рівневий шлюз валют (NBU API $\to$ Local Cache $\to$ Built-in Constants) |
| **Circular Buffer (FIFO)** | Внутрішній масив 50 слотів у `BotTG` | Робота зі стабільною пам'яттю $O(1)$ без витоків |
| **Worker Pool (Producer-Consumer)**| `Executors.newCachedThreadPool()` | Асинхронне надсилання важких альбомів без зупинки парсингу |
| **Observer** | Telegram API `onUpdateReceived()` | Реактивна обробка команд користувачів (`/language`, `/currency`) |
| **Singleton** | `SettingsManager` | Гарантія єдиного джерела конфігурації в системі |
