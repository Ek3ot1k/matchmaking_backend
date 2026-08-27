# Football Matchmaker — Backend

Backend для Telegram Mini App, в котором игроки находят футбольные матчи, собирают команды и ведут подтверждённую статистику. Проект отвечает за авторизацию через Telegram, правила матчей, рейтинги, голосования, платежи Telegram Stars и уведомления бота.

**Production:** [Mini App](https://football-league-ruby.vercel.app) · [API](https://matchmaking-backend-7ifg.onrender.com) · [OpenAPI / Swagger](https://matchmaking-backend-7ifg.onrender.com/swagger-ui/index.html)

## Что умеет приложение

- Создание матчей 5×5, 6×6 и 7×7 с меткой площадки на карте, календарём и защитой от пересечения времени на одной площадке.
- Запись игроков на конкретную позицию: вратарь, защитник, полузащитник, нападающий или вингер. Позиция хранится снимком в участии матча и не меняет историю.
- Автоматическая балансировка команд по числу игроков, позициям и общему рейтингу. Вратарь не обязателен, поэтому матч не блокируется из-за нехватки игроков на позиции.
- Капитаны и схемы: организатор — капитан белой команды; капитан тёмной команды выбирается по рейтингу. До начала матча капитаны могут выбрать схему.
- Серверный старт матча по расписанию и фиксированный игровой таймер 15 минут.
- Протокол матча: организатор указывает счёт, голы и ассисты; участники подтверждают или оспаривают результат в течение 3 часов. Для официального результата нужно не менее 70% подтверждений.
- Номинации после официального матча: MVP, самый быстрый, лучший вратарь, защитник, ассистент, дриблёр и Fair Play.
- Статистика и FUT-карточка: OVR и характеристики зависят от подтверждённых матчей, голов, ассистов, оценок и голосований.
- Дисциплина: предупреждения за неявку, временные и постоянные баны, ограничения организатора — до 3 созданных и 3 отменённых матчей в сутки по Москве.
- Чат конкретного матча, уведомления участникам и VIP на 30 дней через Telegram Stars.

## Как считается рейтинг

Оценка за матч начинается с `6.0`. На неё влияют результат команды, голы, ассисты и «сухой» матч для вратарей и защитников. После подтверждения результата пересчитываются игровые характеристики:

| Событие | Изменение |
| --- | --- |
| Гол | удар (`SHO`) |
| Ассист | пас (`PAS`) |
| Голос MVP | физика (`PHY`) |
| Голос «самый быстрый» | скорость (`PAC`) |
| Высокая/низкая оценка | дриблинг (`DRI`) |
| Игра в обороне и «сухарь» | защита (`DEF`) |

`OVR` пересчитывается из характеристик с весами, зависящими от амплуа игрока. В профиле отображается FUT-карточка, позиции в официальных матчах, последние игры и общая статистика.

## Стек

- Java 21, Spring Boot 4, Spring MVC, Spring Security и JWT.
- Spring Data JPA + PostgreSQL.
- Redis для кеша.
- Telegram Bot API: webhook в production, long polling в local-профиле.
- Telegram Stars для VIP-подписки.
- Maven, Docker, Testcontainers, JUnit 5 и Mockito.
- OpenAPI / Swagger через springdoc.

## Быстрый запуск локально

### 1. Требования

- JDK 21;
- Docker Desktop (для PostgreSQL, Redis и интеграционных тестов);
- токен Telegram-бота, если нужен реальный бот.

### 2. Поднять зависимости

```bash
docker compose up -d postgres redis
```

### 3. Создать `.env`

Файл `.env` лежит в корне backend и автоматически читается Spring Boot. Не добавляйте его в Git.

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/football
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=replace-with-a-long-random-secret
TELEGRAM_BOT_TOKEN=replace-with-bot-token
TELEGRAM_BOT_USERNAME=your_bot_username
TELEGRAM_MINIAPP_URL=http://localhost:5173

# Local profile is enabled by default. Set true only for a separate local bot:
# it must not share a token with the production webhook.
TELEGRAM_LONG_POLLING_ENABLED=false
```

### 4. Запустить приложение

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```bat
mvnw.cmd spring-boot:run
```

API будет доступно на `http://localhost:8080`, Swagger — на `http://localhost:8080/swagger-ui/index.html`.

## Конфигурация окружения

| Переменная | Назначение |
| --- | --- |
| `PORT` | HTTP-порт, по умолчанию `8080` |
| `SPRING_PROFILES_ACTIVE` | Профиль: `local` или `prod` |
| `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD` | Подключение к PostgreSQL |
| `REDIS_HOST` / `PORT` / `PASSWORD` | Подключение к Redis |
| `JWT_SECRET` | Секрет подписи JWT |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_BOT_USERNAME` | Данные Telegram-бота |
| `TELEGRAM_MINIAPP_URL` | URL фронтенда Mini App |
| `TELEGRAM_WEBHOOK_URL` | Публичный URL webhook в production |
| `APP_CORS_ALLOWED_ORIGIN` | Разрешённый origin фронтенда |
| `TELEGRAM_LONG_POLLING_ENABLED` | Включает long polling только для local-профиля |

## Основные API-маршруты

Все маршруты, кроме Telegram-авторизации и webhook, требуют заголовок:

```http
Authorization: Bearer <jwt-token>
```

| Область | Примеры |
| --- | --- |
| Авторизация | `POST /api/v1/auth/telegram` |
| Матчи | `GET/POST /api/v1/matches`, `POST /api/v1/matches/{id}/join`, `POST /api/v1/matches/{id}/finish` |
| Результаты | `GET /api/v1/matches/{id}/result-voting`, `POST /api/v1/matches/{id}/result-voting/vote` |
| Номинации | `POST /api/v1/matches/{id}/vote` |
| Чат матча | `GET/POST /api/v1/matches/{id}/chat` |
| Профиль и рейтинг | `GET /api/v1/users/me/profile`, `GET /api/v1/leaderboards/*` |
| VIP | `POST /api/v1/payments/vip-link` |
| Telegram webhook | `POST /api/v1/telegram/webhook` |

Полная схема запросов и DTO доступна в Swagger после запуска приложения.

## Тесты

Быстрая проверка бизнес-логики без production-БД:

```bash
./mvnw -Dtest=BotMessagesTest,CompleteMatchFlowTest,TelegramStarsClientTest test
```

`CompleteMatchFlowTest` покрывает полный путь официального матча: создание, набор 10 игроков, балансировку 5×5, старт, протокол, подтверждение результата, статистику и номинации.

Полный набор тестов:

```bash
./mvnw test
```

Для `BackendApplicationTests` нужен запущенный Docker, так как тест использует Testcontainers с PostgreSQL.

## Деплой

Backend рассчитан на Docker-деплой в Render:

1. Подключите GitHub-репозиторий как Web Service.
2. Render использует `Dockerfile` из корня проекта.
3. Добавьте production-переменные окружения из таблицы выше и установите `SPRING_PROFILES_ACTIVE=prod`.
4. В BotFather укажите webhook: `https://<your-api-domain>/api/v1/telegram/webhook`.
5. В `APP_CORS_ALLOWED_ORIGIN` укажите production-адрес фронтенда Vercel.

Пуш в `master` запускает автодеплой текущего production-сервиса.

## Правила проекта

- Не коммитьте `.env`, токены Telegram, JWT-секреты и данные production-БД.
- Любой результат становится официальным только после голосования участников.
- Накрутка статистики ведёт к перманентной блокировке.

