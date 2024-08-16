# Property Searcher 3.0 (Enterprise Edition)

Сучасний повнофункціональний бекенд-сервіс моніторингу нерухомості з платформ на **Java 21** та **Spring Boot 3**. Проект спроектовано за принципами Clean Architecture з підтримкою Telegram Bot API, реляційної бази даних, механізму дедуплікації та контейнеризації.

---

## 🚀 Стек технологій (Рівень Middle / Senior)
* **Мова & Платформа:** Java 21 (Virtual Threads ready, Records, Pattern Matching)
* **Фреймворк:** Spring Boot 3.3.4 (Data JPA, Web, Validation, Actuator)
* **База даних:** PostgreSQL (Production) / H2 Embedded (Local/Testing), міграції Flyway
* **Патерни проектування:** Strategy Pattern (Scraper Engine), Repository, Dependency Injection, Builder, DTO
* **Парсинг:** Jsoup з розширеними заголовками та обробкою CloudFront 403
* **Бот:** TelegramBots Spring Boot Starter
* **Тестування:** JUnit 5, Mockito
* **DevOps:** Docker, Docker Compose (multi-stage build)

---

## 📁 Структура архітектури

```
src/main/java/com/propertyseacher
├── PropertySeacherApplication.java   # Головна точка входу Spring Boot
├── api                               # REST API Controllers (Swagger / Frontend ready)
│   └── PropertyController.java
├── config                            # Конфігурації (Telegram, Data Initializer, Beans)
│   ├── DataInitializerConfig.java   # Авто-міграція зі старого settings.txt
│   └── TelegramBotConfig.java
├── domain                            # Domain Model & Repositories
│   ├── entity
│   │   ├── DiscoveredProperty.java   # Знайдені оголошення з індексами унікальності
│   │   ├── SearchTarget.java         # Напрямки та фільтри моніторингу
│   │   └── User.java                 # Telegram-підписники
│   └── repository
│       ├── DiscoveredPropertyRepository.java
│       ├── SearchTargetRepository.java
│       └── UserRepository.java
├── scraper                           # Модуль парсингу (Strategy Pattern)
│   ├── PropertyDto.java              # Незмінний Record
│   ├── ScraperService.java           # Фасад керування стратегіями
│   └── strategy
│       ├── OlxScraperStrategy.java   # Бойовий парсер OLX
│       ├── MockScraperStrategy.java  # Mock-стратегія для тестів та автономної роботи
│       └── ScraperStrategy.java      # Інтерфейс стратегії
├── service                           # Сервісний шар
│   └── TrackingService.java          # Планувальник (Scheduler), дедуплікація, збереження
└── telegram                          # Взаємодія з Telegram
    ├── PropertyTelegramBot.java      # Бот (Long Polling, обробка команд /start, /targets)
    └── TelegramNotificationService.java # Асинхронні повідомлення підписникам
```

---

## 🛠️ Як запустити

### Варіант 1: Локальний запуск (Maven)
```bash
cd "план 2/property-seacher-backend"
mvn spring-boot:run
```
* Додаток автоматично підхопить конфігурацію зі старого `settings.txt` (якщо він є) та перенесе цілі пошуку в базу даних.
* Вбудована H2 консоль доступна за адресою: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/property_db`, User: `sa`, Password: порожній).
* Actuator Health Check: `http://localhost:8080/actuator/health`.

### Варіант 2: Запуск у Docker Compose (Full Stack)
```bash
cd "план 2/property-seacher-backend"
docker compose up -d --build
```
Запускає ізольований контейнер застосунку разом із повноцінною базою даних PostgreSQL 16.

---

## 🧪 Запуск тестів
```bash
mvn test
```
Тести покривають:
1. Виявлення нового оголошення та надсилання сповіщення у Telegram.
2. Дедуплікацію (ігнорування оголошень, які вже є в базі, без повторних відправок).

---

## 💡 REST API Ендпоінти
* `GET /api/v1/properties?page=0&size=20` — сторінковий перегляд знайдених оголошень.
* `GET /api/v1/targets` — перегляд активних пошукових напрямків.
* `POST /api/v1/targets` — додавання нового пошукового напрямку (наприклад, з фронтенду або мобільного застосунку).
* `POST /api/v1/track/trigger` — ручний примусовий запуск циклу перевірки.
