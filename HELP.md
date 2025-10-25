### Переменные окружения `(значение по умолчанию)`

* **RF_CONTRACT_SVC_HOSTNAME** `(localhost)` - *внутреннее имя хоста сервиса*
* **RF_CONTRACT_SVC_HOSTNAME_EXTERNAL** `(localhost)` - *наружное имя хоста сервиса*
* **RF_AUTH_SVC_HOSTNAME** `(localhost)` - *имя хоста сервиса авторизации*
* **RF_EUREKA_SERVICE_URL** `(http://localhost:8761/eureka/)` - *адрес сервиса Eureka*
* **RF_CONFIG_SERVICE_URL** `(http://localhost:8888)` - *адрес сервиса конфигурации*
* **RF_SCHEMA_REGISTRY_URL** `(http://kafka.micxem:8081)` - *адрес сервиса Schema Registry*
* **RF_KAFKA_BROKERS** `(kafka.micxem:9092)` - *список брокеров Kafka*
* **RF_PG_CONTRACT_DB_SVC** `(postgresql.micxem)` - *имя сервиса базы данных*
* **RF_PG_CONTRACT_DB_SVC_PORT** `(5432)` - *порт сервиса базы данных*
* **PG_DB_USER** `(rightsflow)` - *имя пользователя базы данных*
* **PG_DB_PASSWORD** - *пароль пользователя базы данных*
* **SWAGGER_CLIENT_SECRET** - *секрет клиента Swagger*
* **RF_OTLP_ENDPOINT** `(http://tempo.monitoring:4318/v1/traces)` - *адрес сервиса OpenTelemetry*
* **RF_TRACE_ENABLED** `(false)` - *флаг включения трассировки*