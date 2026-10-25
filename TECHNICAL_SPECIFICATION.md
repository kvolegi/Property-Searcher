# Технічна специфікація: Property Searcher 3 🏢⚡
> **Стандарт:** ISO/IEC/IEEE 29148:2018 (Systems and software engineering — Life cycle processes — Requirements engineering)  
> **Версія:** 3.0 (Фінальний реліз)  
> **Статус:** Production-Ready  
> **Головний інженер / Lead Architect:** `kvolegi`  
> **Стек технологій:** Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, PostgreSQL 16, Flyway 10, Jsoup 1.17, TelegramBots 6.8, Maven 3.9, Docker 26+.

---

## 1. Архітектурне призначення та бізнес-метрики (SLA / SLO)

Система призначена для безперервного низьколатентного збору, валідації, аналізу та трансляції оголошень вторинного ринку нерухомості з публічних агрегаторів (OLX.ua, DOM.RIA.com) у канали Telegram рієлторів та інвесторів.

| Показник (SLO) | Цільове значення | Механізм забезпечення |
| :--- | :--- | :--- |
| **Detection Latency** | $\le 1.8\text{ c}$ після появи на CDN Edge | Реактивний polling-воркер з адаптивним темпом |
| **Dispatch Latency** | $\le 500\text{ мс}$ (SendMediaGroup) | Асинхронний пул потоків `notificationExecutor` |
| **False-Positive Spam Rate** | $0.00\%$ | 30-елементний Silent Baseline при старті |
| **Duplicate Delivery Rate** | $0.00\%$ | 50-елементний кільцевий буфер $O(1)$ |
| **HTTP 429 / WAF Block Rate** | $0.00\%$ | Adaptive Token-Bucket ($\le 2.20\text{ req/s}$) + Phase Staggering |
| **Memory Footprint** | $\le 45\text{ MB}$ (Desktop) / $\le 250\text{ MB}$ (Server) | Zero-download CDN media streaming, G1GC |

---

## 2. Модульна декомпозиція системи

```text
Property Searcher 3/
├── core-desktop/                   # Нативний автономний клієнт (Zero-Cloud Dependency)
│   ├── src/main/java/PropertySeacher/
│   │   ├── BotGUI.java             # UI Swing/AWT (Bilingual UA/EN, Modern Flat Theme)
│   │   ├── BotTG.java              # Ядро моніторингу, Telegram Dispatcher, рейт-контролер
│   │   ├── ParserOLX.java          # HTML/DOM Ingestion адаптер для OLX.ua
│   │   ├── CurrencyService.java    # 3-рівневий шлюз курсів валют НБУ (Кеш 24h)
│   │   ├── SettingsManager.java    # Синхронізація налаштувань, конфігів та мови
│   │   ├── I18n.java               # Двомовний словник локалізації (UA / EN)
│   │   ├── CategoryCatalog.java    # Ієрархічний каталог міст, районів та типів нерухомості
│   │   └── SmartCategoryBuilderDialog.java # Візуальний конструктор URL-фільтрів
│   └── pom.xml
│
├── enterprise-backend/             # Хмарний бекенд мікросервісної архітектури
│   ├── src/main/java/com/propertyseacher/
│   │   ├── api/PropertyController.java     # REST API контролер з OpenAPI 3.0 анотаціями
│   │   ├── domain/entity/          # JPA-сутності (User, SearchTarget, DiscoveredProperty, PriceHistory)
│   │   ├── domain/repository/      # Spring Data JPA репозиторії
│   │   ├── scraper/strategy/       # Патерн Strategy (OlxScraperStrategy, DomRiaScraperStrategy)
│   │   ├── analytics/PriceAnalyticsService.java # Статистична аналітика волатильності цін
│   │   └── telegram/PropertyTelegramBot.java   # Spring-керований клієнт сповіщень
│   ├── Dockerfile & docker-compose.yml
│   └── pom.xml
│
└── dist/                           # Готові бінарні артефакти
    ├── PropertySeacher.app         # Нативний macOS Bundle з вбудованим JRE
    ├── PropertySeacher-Universal.jar # Універсальний виконуваний кросплатформний JAR
    ├── PropertySeacher-Enterprise-Server.jar # Серверний виконуваний JAR Spring Boot 3
    ├── Run_Windows.bat & Run_Linux.sh # Автоматичні скрипти запуску
    └── assets/                     # Графічні іконсет-пакети (.icns, .ico, .png)
```

---

## 3. Деталізація алгоритмів ядра (Core Algorithms)

### 3.1. Adaptive Token-Bucket Rate Controller
* **Математична модель:**
  При одночасному опитуванні $N$ цільових категорій, системний інтервал опитування $\tau_i$ для $i$-го потоку обчислюється динамічно:
  $$\tau = \max\left(\tau_{\min},\; \left\lceil \frac{N \times 1000}{R_{\max}} \right\rceil\right)$$
  де:
  * $\tau_{\min} = 1800\text{ мс}$ (мінімальний інтервал для одиночного потоку);
  * $R_{\max} = 2.20\text{ req/s}$ (емпірично вивірений поріг безпеки від систем CloudFront WAF);
  * $N$ — кількість активних категорій у `settings.txt`.
* **Фазовий зсув (Phase Staggering):**  
  Старт потоків розноситься у часі з кроком $\Delta t = \frac{\tau}{N}$. Це виключає збіг запитів у часі та запобігає імпульсним стрибкам трафіку.
* **Anti-Fingerprint Jitter:**  
  До кожного запиту додається стохастична дельта $\delta \in [-150\text{ мс}, +150\text{ мс}]$, що ламає строгий метрономний таймінг бота.

### 3.2. 3-Tier Resilient Currency Engine (Шлюз НБУ)
Архітектура розрахована на нерегулярний режим роботи хоста (сервер/ноутбук вмикається спорадично):

```text
[Запит конвертації]
       │
       ▼
 [Перевірка TTL кешу (currency_cache.json)]
       ├─── Дійсний (< 24 год) ───────────► Застосування курсів (0 мс)
       └─── Прострочений (> 24 год) або відсутній
                 │
                 ▼
        [Tier 1: bank.gov.ua API] (Timeout 3000ms)
                 ├─── Успіх (200 OK) ────► Оновлення JSON-кешу + Застосування
                 └─── Помилка / Офлайн
                           │
                           ▼
                  [Tier 2: Застарілий кеш] ──► Застосування з попередженням у лог
                           │
                           └─── Файл відсутній
                                     │
                                     ▼
                            [Tier 3: Константи] ──► USD=41.60, EUR=45.20
```

* **Режим форматування DUAL:**
  * Вхід: `$65 000` $\to$ Вихід: `$65 000 (≈ 2 704 000 грн)`
  * Вхід: `2 500 000 грн` $\to$ Вихід: `2 500 000 грн (≈ $60 096)`
  * Числа форматуються з групуванням розрядів пробілами (`1 000 000`).

### 3.3. 50-Slot Circular Buffer & 30-Item Silent Baseline
* **Silent Baseline:** Під час першого запиту до категорії бот отримує перші 30 лістингів і записує їхні ID у хеш-сет категорії, **не генеруючи жодних сповіщень**. Це гарантує нульовий спам старими даними.
* **Circular Buffer:** Кільцевий масив на 50 слотів пам'яті для кожної категорії витісняє найстаріші записи за правилом FIFO. Перевірка наявності ID виконується за час $O(1)$.
* **Тільки органічний трафік:** Парсер відкидає вузли з атрибутами `[data-testid="ad-card-featured"]` або маркерами «ТОП», гарантуючи надходження лише свіжих публікацій.
* **Adaptive Burst Sniping:** При виявленні нового лістингу бот робить позачерговий повторний чек через 500 мс, щоб перехопити пачку оголошень від одного автора.

### 3.4. Асинхронний MediaGroup Dispatcher
* Відправка фотоальбомів (до 10 знімків) делегується у `Executors.newCachedThreadPool()`.
* **Zero-Download Architecture:** Бот передає прямі CDN-посилання `InputMediaPhoto(cdnUrl)`. Клієнт не завантажує байти фотографій на диск, не витрачає RAM і не навантажує локальний інтернет-канал.
* **ParseMode.HTML Sanitization:** Всі поля назви, опису та локації екрануються (`<` $\to$ `&lt;`, `>` $\to$ `&gt;`, `&` $\to$ `&amp;`), що унеможливлює падіння з помилкою `400 Bad Request`.

### 3.5. Night Silent Mode (Нічний режим)
* Часовий пояс: `Europe/Kyiv` (EET/EEST).
* У проміжку `23:00 <= currentHour || currentHour < 07:00` метод `SendMediaGroup` отримує прапорець `setDisableNotification(true)`. Повідомлення з'являються в чаті без звуку та вібрації.

---

## 4. Схема бази даних Enterprise-бекенду (ERD)

```text
  ┌────────────────────────────────────────────────────────┐
  │                         users                          │
  ├────────────────────────────┬───────────────────────────┤
  │ id                         │ BIGSERIAL PRIMARY KEY     │
  │ telegram_chat_id           │ BIGINT UNIQUE NOT NULL    │
  │ first_name                 │ VARCHAR(128)              │
  │ preferred_language         │ VARCHAR(10) DEFAULT 'UA'  │
  │ is_active                  │ BOOLEAN DEFAULT TRUE      │
  │ created_at                 │ TIMESTAMP WITH TIME ZONE  │
  └────────────────────────────┴───────────────────────────┘
                               │ 1
                               │
                               │ N
  ┌────────────────────────────▼───────────────────────────┐
  │                     search_targets                     │
  ├────────────────────────────┬───────────────────────────┤
  │ id                         │ BIGSERIAL PRIMARY KEY     │
  │ user_id                    │ BIGINT REFERENCES users   │
  │ name                       │ VARCHAR(255) NOT NULL     │
  │ target_url                 │ TEXT NOT NULL             │
  │ source_portal              │ VARCHAR(32) NOT NULL      │
  │ is_active                  │ BOOLEAN DEFAULT TRUE      │
  │ polling_interval_sec       │ INT DEFAULT 2             │
  │ created_at                 │ TIMESTAMP WITH TIME ZONE  │
  └────────────────────────────┴───────────────────────────┘
                               │ 1
                               │
                               │ N
  ┌────────────────────────────▼───────────────────────────┐
  │                  discovered_properties                 │
  ├────────────────────────────┬───────────────────────────┤
  │ id                         │ BIGSERIAL PRIMARY KEY     │
  │ target_id                  │ BIGINT REFERENCES targets │
  │ external_id                │ VARCHAR(64) UNIQUE NOT NULL│
  │ title                      │ VARCHAR(512) NOT NULL     │
  │ current_price              │ NUMERIC(14,2) NOT NULL    │
  │ currency                   │ VARCHAR(8) NOT NULL       │
  │ location                   │ VARCHAR(255)              │
  │ url                        │ TEXT NOT NULL             │
  │ image_urls_json            │ TEXT                      │
  │ published_at               │ TIMESTAMP WITH TIME ZONE  │
  │ discovered_at              │ TIMESTAMP WITH TIME ZONE  │
  └────────────────────────────┴───────────────────────────┘
                               │ 1
                               │
                               │ N
  ┌────────────────────────────▼───────────────────────────┐
  │                      price_history                     │
  ├────────────────────────────┬───────────────────────────┤
  │ id                         │ BIGSERIAL PRIMARY KEY     │
  │ property_id                │ BIGINT REFERENCES prop    │
  │ price                      │ NUMERIC(14,2) NOT NULL    │
  │ currency                   │ VARCHAR(8) NOT NULL       │
  │ recorded_at                │ TIMESTAMP WITH TIME ZONE  │
  └────────────────────────────┴───────────────────────────┘
```

---

## 5. RESTful API Специфікація (Enterprise Backend)

Усі ендпоінти повертають JSON і задокументовані через Swagger UI (`/swagger-ui.html`).

| Метод | URL | Опис | Тіло запиту / Параметри | Статус відповіді |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/properties` | Список знайдених об'єктів | `page`, `size`, `sortBy` | `200 OK` |
| `GET` | `/api/v1/properties/{id}` | Деталі оголошення з фото | `id` (path) | `200 OK` / `404 Not Found` |
| `GET` | `/api/v1/properties/{id}/history` | Історія зміни цін | `id` (path) | `200 OK` |
| `POST` | `/api/v1/targets` | Створення нової цілі моніторингу | `{"name":"Київ 1к", "url":"..."}` | `201 Created` |
| `DELETE` | `/api/v1/targets/{id}` | Видалення цілі моніторингу | `id` (path) | `204 No Content` |
| `GET` | `/actuator/health` | Перевірка стану (Healthcheck) | — | `200 OK` (`UP` / `DOWN`) |
| `GET` | `/actuator/metrics` | Системні метрики JVM та HikariCP | — | `200 OK` |

---

## 6. Фізика мережевого кешування (CDN Edge Propagation Physics)

```text
[Продавець натискає "Опублікувати"]
                │
                ▼
  [Внутрішня БД порталу OLX / DOM.RIA]
                │
                ▼ (Модерація, AI антиспам, 30–90 секунд)
  [Next.js Dynamic SSR Render]
                │
                ▼
  [Публікація на CDN Edge Nodes (Akamai / Cloudflare)] <─── Фактична поява у вебі!
                │
                ▼ (1.8 секунди — детекція)
  [Property Searcher 3 Sniping Engine]
                │
                ▼ (300 мс — асинхронний SendMediaGroup)
  [Telegram рієлтора: Фотоальбом 10 фото]
```

> [!IMPORTANT]
> Затримка 30–90 секунд між публікацією продавцем і появою на нодах CDN є фізичним процесом модерації на боці порталу.  
> **Property Searcher фіксує оголошення у перші 1–2 секунди після його фактичної появи на публічних CDN-нодах.** Швидше за це не працює жодна система у світі, оскільки до виходу з модерації оголошення фізично не існує в публічній мережі Internet.

---

## 7. Конфігураційна матриця (Settings & Environment Matrix)

| Параметр | Файл | Тип | За замовчуванням | Опис |
| :--- | :--- | :--- | :--- | :--- |
| `app.language` | `settings.txt` | String | `UA` | Мова інтерфейсу (`UA` або `EN`) |
| `currency.mode` | `settings.txt` | String | `DUAL` | Режим відображення цін (`ORIGINAL`, `UAH`, `USD`, `DUAL`) |
| `bot.token` | `settings.txt` / ENV | String | — | Токен Telegram Bot API |
| `bot.username`| `settings.txt` / ENV | String | `@Property_Searcher_bot` | Юзернейм бота |
| `SPRING_PROFILES_ACTIVE` | ENV | String | `prod` | Активний профіль Spring Boot |
| `SPRING_DATASOURCE_URL` | ENV | String | `jdbc:postgresql://...` | Рядок підключення до БД |
| `SERVER_PORT` | ENV | Integer | `8080` | Порт веб-сервера |
| `TARGET_SAFE_GLOBAL_RPS` | Код | Double | `2.20` | Максимальний безпечний глобальний ліміт RPS |
