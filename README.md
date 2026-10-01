# LOPON

Веб-приложение для разбора велотренировок из FIT-файлов. Бэкенд на Java 25 и Spring Boot, база PostgreSQL, фронтенд
на Vite и Chart.js.

## Запуск
1. Создать `.env` из образца и задать в нем `DB_PASSWORD` и `JWT_SECRET`:
Пароль может быть любым, а `JWT_SECRET` должен быть строкой base64, в которой закодировано не меньше 32 байт.
2. Собрать и запустить контейнеры:
```bash
docker compose up --build -d
```
Поднимутся `db`, `migrate` (применяет миграции и завершается), `backend` и `frontend`. В выводе `docker compose ps` у `backend` должен быть статус healthy, у `migrate` exited (0).
3. Открыть http://localhost:8000, зарегистрироваться и загрузить FIT-файлы. Swagger UI доступен по адресу http://localhost:8000/api/docs.
Остановить: `docker compose down`. Данные останутся в томе `pgdata`, команда `docker compose down -v` удалит и их.
