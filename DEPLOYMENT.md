# 🚀 Property Searcher 3 — Server Deployment & Operations Guide

> **Author**: `kvolegi`  
> **Target Environment**: Linux (Ubuntu 22.04 / 24.04 LTS, Debian 12, RHEL 9)  
> **Minimal System Requirements**: 1 vCPU, 2 GB RAM, 10 GB SSD, OpenJDK 21 LTS or Docker 24+

---

## 📑 Зміст (Table of Contents)
1. [Передумови та налаштування сервера](#1-передумови-та-налаштування-сервера)
2. [Спосіб 1: Розгортання через Docker Compose (Рекомендований)](#2-спосіб-1-розгортання-через-docker-compose-рекомендований)
3. [Спосіб 2: Розгортання як нативний Linux Systemd Daemon](#3-спосіб-2-розгортання-як-нативний-linux-systemd-daemon)
4. [Конфігурація Nginx Reverse Proxy та Let's Encrypt SSL](#4-конфігурація-nginx-reverse-proxy-та-lets-encrypt-ssl)
5. [Синхронізація таймзони (Критично для Night Silent Mode)](#5-синхронізація-таймзони-критично-для-night-silent-mode)
6. [Моніторинг, Healthcheck та Логування](#6-моніторинг-healthcheck-та-логування)
7. [Резервне копіювання БД (Automated Backup)](#7-резервне-копіювання-бд-automated-backup)
8. [Zero-Downtime Оновлення (Deployment Script)](#8-zero-downtime-оновлення-deployment-script)

---

## 1. Передумови та налаштування сервера

Підключіться до вашого VPS/VDS через SSH:
```bash
ssh root@your-server-ip
```

Оновіть системні пакети та встановіть базові утиліти:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git htop ufw tzdata
```

Налаштуйте фаєрвол UFW:
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow ssh
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## 2. Спосіб 1: Розгортання через Docker Compose (Рекомендований)

Цей спосіб підіймає повністю ізольоване виробниче середовище: **Spring Boot 3 + PostgreSQL 16 Alpine + Flyway міграції + Swagger UI + Telegram Polling Service**.

### Крок 2.1. Встановлення Docker та Docker Compose
```bash
# Встановлення офіційного рушія Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo systemctl enable --now docker

# Перевірка
docker --version && docker compose version
```

### Крок 2.2. Завантаження проєкту та налаштування оточення
Перейдіть у каталог бекенду:
```bash
mkdir -p /opt/property-searcher
cd /opt/property-searcher
```

Створіть файл `.env` із вашими параметрами:
```bash
cat << 'EOF' > .env
TELEGRAM_BOT_USERNAME=Property_Searcher_bot
TELEGRAM_BOT_TOKEN=YOUR_BOT_TOKEN_FROM_BOTFATHER
SPRING_PROFILES_ACTIVE=prod

POSTGRES_DB=property_db
POSTGRES_USER=property_admin
POSTGRES_PASSWORD=YourStrongDatabasePassword123!
POSTGRES_PORT=5432
EOF
```

### Крок 2.3. Запуск контейнерів у фоновому режимі (Daemon)
```bash
docker compose up -d --build
```

### Крок 2.4. Перевірка статусу та логів
```bash
# Перегляд статусу контейнерів
docker compose ps

# Живий перегляд логів додатка
docker compose logs -f app
```

Зупинка / перезапуск:
```bash
docker compose down          # Зупинити
docker compose restart app   # Перезапустити бекенд
```

---

## 3. Спосіб 2: Розгортання як нативний Linux Systemd Daemon

Якщо ви віддаєте перевагу роботі без Docker, запускайте скомпільований `PropertySeacher-Enterprise-Server.jar` через системний менеджер служб `systemd`.

### Крок 3.1. Встановлення OpenJDK 21 LTS та PostgreSQL
```bash
# Встановлення Java 21
sudo apt install -y openjdk-21-jre-headless postgresql postgresql-contrib

# Створення бази даних
sudo -u postgres psql -c "CREATE USER property_admin WITH PASSWORD 'YourStrongDatabasePassword123!';"
sudo -u postgres psql -c "CREATE DATABASE property_db OWNER property_admin;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE property_db TO property_admin;"
```

### Крок 3.2. Створення системного користувача та розміщення файлів
```bash
sudo useradd -r -s /bin/false propertyseacher
sudo mkdir -p /opt/property-searcher
sudo cp PropertySeacher-Enterprise-Server.jar /opt/property-searcher/app.jar
sudo chown -R propertyseacher:propertyseacher /opt/property-searcher
```

Створіть конфігураційний файл `/opt/property-searcher/app.env`:
```bash
sudo bash -c 'cat << EOF > /opt/property-searcher/app.env
TELEGRAM_BOT_USERNAME=Property_Searcher_bot
TELEGRAM_BOT_TOKEN=YOUR_BOT_TOKEN_FROM_BOTFATHER
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/property_db
SPRING_DATASOURCE_USERNAME=property_admin
SPRING_DATASOURCE_PASSWORD=YourStrongDatabasePassword123!
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
EOF'
sudo chmod 600 /opt/property-searcher/app.env
sudo chown propertyseacher:propertyseacher /opt/property-searcher/app.env
```

### Крок 3.3. Створення служби Systemd
Створіть файл `/etc/systemd/system/propertyseacher.service`:
```ini
[Unit]
Description=Property Searcher 3 Enterprise Daemon
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=propertyseacher
Group=propertyseacher
WorkingDirectory=/opt/property-searcher
EnvironmentFile=/opt/property-searcher/app.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -XX:+UseG1GC -jar /opt/property-searcher/app.jar
Restart=always
RestartSec=5s
SuccessExitStatus=143

# Захист безпеки Linux
ProtectSystem=full
ProtectHome=true
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### Крок 3.4. Активація та запуск служби
```bash
sudo systemctl daemon-reload
sudo systemctl enable propertyseacher
sudo systemctl start propertyseacher

# Перевірка статусу
sudo systemctl status propertyseacher

# Журнал логів у реальному часі
journalctl -u propertyseacher -f
```

---

## 4. Конфігурація Nginx Reverse Proxy та Let's Encrypt SSL

Для безпечного доступу до REST API та Swagger UI налаштуйте Nginx:

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Створіть конфігурацію `/etc/nginx/sites-available/property-searcher.conf`:
```nginx
server {
    listen 80;
    server_name api.yourdomain.com; # Замініть на ваш домен або IP

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket підтримка (для real-time оновлень)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Активуйте сайт і отримайте безкоштовний SSL-сертифікат:
```bash
sudo ln -s /etc/nginx/sites-available/property-searcher.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.yourdomain.com
```

Тепер Swagger UI доступний за адресою:
`https://api.yourdomain.com/swagger-ui/index.html`

---

## 5. Синхронізація таймзони (Критично для Night Silent Mode)

Алгоритм **Night Silent Mode** вимикає сповіщення між **23:00 та 07:00** за київським часом (`Europe/Kyiv`). Обов'язково встановіть правильну часову зону на сервері:

```bash
sudo timedatectl set-timezone Europe/Kyiv

# Перевірка
timedatectl status
```

У логах час має збігатися з офіційним київським часом (EET/EEST).

---

## 6. Моніторинг, Healthcheck та Логування

Бекенд оснащено ендпоінтами **Spring Boot Actuator**:
- **Health check**: `GET http://localhost:8080/actuator/health`  
  Повертає `{"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}`.
- **Метрики**: `GET http://localhost:8080/actuator/metrics`
- **Prometheus Scrape**: `GET http://localhost:8080/actuator/prometheus`

### Налаштування Logrotate (Ротація логів)
Створіть `/etc/logrotate.d/propertyseacher`:
```text
/opt/property-searcher/*.log {
    daily
    missingok
    rotate 14
    compress
    delaycompress
    notifempty
    copytruncate
}
```

---

## 7. Резервне копіювання БД (Automated Backup)

Автоматичний щоденний бекап PostgreSQL у 03:00 ночі:
```bash
sudo mkdir -p /var/backups/propertyseacher

cat << 'EOF' | sudo tee /usr/local/bin/backup-property-db.sh
#!/bin/bash
BACKUP_DIR="/var/backups/propertyseacher"
DATE=$(date +"%Y%m%d_%H%M%S")
PGPASSWORD="YourStrongDatabasePassword123!" pg_dump -U property_admin -h localhost property_db | gzip > "$BACKUP_DIR/backup_$DATE.sql.gz"
# Видаляти бекапи старші за 30 днів
find $BACKUP_DIR -name "*.sql.gz" -mtime +30 -exec rm {} \;
EOF

sudo chmod +x /usr/local/bin/backup-property-db.sh
```

Додайте в cron (`crontab -e`):
```text
0 3 * * * /usr/local/bin/backup-property-db.sh > /dev/null 2>&1
```

---

## 8. Zero-Downtime Оновлення (Deployment Script)

Для швидкого оновлення версії створіть `/opt/property-searcher/deploy.sh`:

```bash
#!/bin/bash
set -e

echo "🚀 Starting deployment of Property Searcher 3..."
cd /opt/property-searcher

# Оновлення коду
git pull origin main

# Перезбірка Docker контейнера без простою
docker compose build app
docker compose up -d --no-deps app

echo "🔍 Running healthcheck verification..."
sleep 10
STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || true)

if [ "$STATUS" = "200" ]; then
    echo "✅ Deployment successful! Service is UP."
else
    echo "❌ Healthcheck failed with status $STATUS. Rolling back..."
    docker compose logs --tail=50 app
    exit 1
fi
```
Зробіть виконуваним: `chmod +x /opt/property-searcher/deploy.sh`.
EOF