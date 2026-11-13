# Password Vault

Сервис для безопасного хранения паролей и секретов с REST API на Scala 3 и Typelevel-стеке.

Каждый пользователь видит только свои записи (доступ по JWT). Все пароли шифруются при сохранении с помощью AES-256-GCM, а в списках выдаются только метаданные (название и комментарий). Расшифрованный пароль отдается отдельным эндпоинтом.

## Стек технологий

- **Язык:** Scala 3.3 (LTS)
- **Эффекты и рантайм:** Cats Effect 3
- **HTTP / API:** http4s (Ember), Tapir (OpenAPI / Swagger UI)
- **JSON:** Circe
- **База данных:** PostgreSQL, Doobie, HikariCP, Flyway
- **Безопасность:** AES-256-GCM (JDK Cipher), bcrypt, JWT (HS256)
- **CSV:** fs2-data-csv
- **Тестирование:** MUnit, MUnit Cats Effect, Testcontainers (PostgreSQL)
- **Инфраструктура:** Docker, Docker Compose, GitHub Actions CI

## Быстрый старт

Проще всего запустить проект через Docker Compose:

```bash
cp .env.example .env
docker compose up --build
```

Сервис запустится на порту `8080`:
- API: `http://localhost:8080`
- Swagger UI документация: `http://localhost:8080/docs`

### Локальный запуск через sbt

1. Поднять только базу данных:
```bash
docker compose up -d db
```

2. Задать переменные окружения и запустить приложение:
```bash
export DB_URL="jdbc:postgresql://127.0.0.1:5432/password_vault"
export DB_USER="password_vault"
export DB_PASSWORD="password_vault"
export MASTER_KEY_BASE64="$(openssl rand -base64 32)"
export JWT_SECRET="$(openssl rand -hex 32)"

sbt run
```

> **Важно:** `MASTER_KEY_BASE64` используется для шифрования и расшифровки паролей. Если сменить ключ между перезапусками, ранее сохраненные пароли расшифровать не удастся.

## Переменные окружения

| Переменная | Обязательная | По умолчанию | Описание |
|---|---|---|---|
| `DB_URL` | Да | — | JDBC URL для подключения к PostgreSQL |
| `DB_USER` | Да | — | Пользователь БД |
| `DB_PASSWORD` | Да | — | Пароль БД |
| `MASTER_KEY_BASE64` | Да | — | Ключ AES-256 (32 байта в base64) |
| `JWT_SECRET` | Да | — | Секретная строка для подписи токенов (не менее 32 символов) |
| `HTTP_PORT` | Нет | `8080` | Порт HTTP-сервера |
| `DB_POOL_SIZE` | Нет | `10` | Размер пула соединений HikariCP |
| `JWT_TTL_SECONDS` | Нет | `3600` | Время жизни access-токена (в секундах) |

## API

Все эндпоинты работы с записями (`/passwords/*`) требуют заголовок `Authorization: Bearer <token>`.

| Метод | Эндпоинт | Описание |
|---|---|---|
| `GET` | `/health` | Проверка доступности сервиса |
| `POST` | `/auth/register` | Регистрация пользователя (`login`, `password`) |
| `POST` | `/auth/login` | Вход и получение JWT-токена |
| `GET` | `/passwords` | Список записей (без паролей). Параметры: `search`, `exact`, `limit`, `offset` |
| `POST` | `/passwords` | Создание новой записи |
| `GET` | `/passwords/{id}` | Получение записи по id (без пароля) |
| `GET` | `/passwords/{id}/secret` | Получение расшифрованного пароля |
| `PATCH` | `/passwords/{id}` | Частичное обновление полей |
| `DELETE` | `/passwords/{id}` | Удаление записи (soft delete) |
| `GET` | `/passwords/export` | Экспорт всех записей в CSV |
| `POST` | `/passwords/import` | Импорт записей из CSV (транзакционно) |

Формат ошибок унифицирован:
```json
{
  "error": "Validation failed",
  "details": ["name must not be empty", "password must not be empty"]
}
```

## Примеры использования (cURL)

**1. Регистрация и вход:**
```bash
curl -X POST localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"login":"alice","password":"password123"}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"alice","password":"password123"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
```

**2. Создание записи:**
```bash
curl -X POST localhost:8080/passwords \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"GitHub","password":"secret123","comment":"рабочий аккаунт"}'
```

**3. Просмотр списка и поиск:**
```bash
# Получить список (пароли не возвращаются)
curl localhost:8080/passwords -H "Authorization: Bearer $TOKEN"

# Поиск по подстроке
curl "localhost:8080/passwords?search=git" -H "Authorization: Bearer $TOKEN"

# Точный поиск
curl "localhost:8080/passwords?search=GitHub&exact=true" -H "Authorization: Bearer $TOKEN"
```

**4. Получение пароля:**
```bash
curl localhost:8080/passwords/1/secret -H "Authorization: Bearer $TOKEN"
```

**5. Частичное обновление (PATCH):**
```bash
# Обновить пароль и очистить комментарий
curl -X PATCH localhost:8080/passwords/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"password":"newpassword","comment":null}'
```

**6. Экспорт и импорт CSV:**
```bash
# Экспорт
curl localhost:8080/passwords/export -H "Authorization: Bearer $TOKEN" -o passwords.csv

# Импорт
curl -X POST localhost:8080/passwords/import \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: text/csv" \
  --data-binary @passwords.csv
```

## Тесты

Для запуска тестов необходим работающий Docker (для интеграционных тестов репозитория поднимается реальный PostgreSQL через Testcontainers):

```bash
sbt test
```

Основные тестовые сьюты:
- `CryptoSuite` — шифрование/дешифрование AES-GCM, проверка уникальности IV и отсечение модифицированного шифротекста
- `ValidationSuite`, `DomainSuite` — валидация логинов, паролей, пагинации
- `CsvFormatSuite` — парсинг и генерация RFC 4180 (кавычки, запятые, переносы строк)
- `PasswordServiceSuite`, `AuthSuite` — бизнес-логика авторизации и управления паролями на in-memory репозиториях
- `ApiRoutesSuite` — проверка HTTP-слоя, кодов ответов и обработки ошибок
- `PostgresRepositorySuite` — изоляция пользователей, поиск, пагинация и атомарность батчевой вставки

## Структура проекта

```
src/main/scala/com/example/passwordvault/
├── Main.scala              # Инициализация ресурсов и запуск Ember-сервера
├── config/                 # Чтение конфигурации из окружения
├── auth/                   # Хеширование bcrypt и генерация/проверка JWT
├── crypto/                 # Реализация AES-GCM шифрования
├── db/                     # HikariCP транзактор и накат миграций Flyway
├── domain/                 # Доменные модели, ошибки и валидация
├── repository/             # SQL-запросы на Doobie
├── service/                # Бизнес-логика и обработка CSV
└── http/                   # Описание Tapir эндпоинтов и маршруты Http4s
```

## Автор

Сабиров Камал
