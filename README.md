Это будет и моей дипломной работой на магистратуре.  

# Агрегатор потребительских цен на основе краудсорс подхода  

Человек заходит,  
1. выбирает магазин в котором находится,
2. добавляет товары которые покупает (сканируется штрихкод для начала).   
3. и будет видеть историю своих покупок и цену на этот же товар

* и возможность своих данных экспорт импорт cделать будет  
* ( Яндекс карты использую но если они окажутся жадными то openчтонибудь заюзаю и/или посмотрю у других)  
* Чтоб анонимно все было. логин Будет "пароль есть а кому он принадлежит хз" ну если чел сам себя не спалил.
* Бэк. в основном чтобы хранить список цен на товары. но потом можно прикрутить что угодно-комментарии... фото
* ну и список магазинов будет на сервере. хочу позаимствовать попробовать с местных сетевых магазинов как и получение цены по их API . цель - человеку как можно меньше водить вручную. 
* Ну и модератором сам постараюсь быть 🙈

## Secrets submodule

Секреты теперь предполагаются вне публичного репозитория, в приватном Git submodule `secrets/`.

Используемые файлы:

- `secrets/android/local.properties`
- `secrets/server/external-apis.properties`

`composeApp` читает `SERVER_BASE_URL` и `MAPKIT_API_KEY` сначала из `secrets/android/local.properties`, потом из обычного `local.properties` / Gradle properties.

Сервер читает внешние API-настройки сначала из `secrets/server/external-apis.properties`, потом из переменных окружения:

- `PRICE_LOOKUP_BASE_URL`
- `PRICE_LOOKUP_SESSION_QUERY`
- `BARCODE_LOOKUP_BASE_URL`
- `BARCODE_LOOKUP_SECRET`

Пример `secrets/android/local.properties`:

```properties
SERVER_BASE_URL=https://195.46.171.236:9878
MAPKIT_API_KEY=your-mapkit-key
```

Пример `secrets/server/external-apis.properties`:

```properties
PRICE_LOOKUP_BASE_URL=https://example.com/api/v1/price/71
PRICE_LOOKUP_SESSION_QUERY=&session_id=...&user_id=...
BARCODE_LOOKUP_BASE_URL=https://barcodes.olegon.ru/api/card/name
BARCODE_LOOKUP_SECRET=your-secret
```

### GitHub setup

1. Создай приватный репозиторий, например `pokupan-secrets`.
2. Внутри него положи нужные файлы `android/local.properties` и `server/external-apis.properties`.
3. В основном репозитории добавь submodule:

```bash
git submodule add git@github.com:<your-account>/pokupan-secrets.git secrets
git commit -m "Add private secrets submodule"
```

4. После клона проекта инициализируй submodule:

```bash
git submodule update --init --recursive
```

## HTTPS

Android 9+ блокирует `http://` по умолчанию, поэтому клиент и сервер теперь надо поднимать через HTTPS.

Сервер:

```bash
export SERVER_PORT=9878
export SSL_KEYSTORE_PATH=/absolute/path/server-keystore.p12
export SSL_KEYSTORE_PASSWORD=changeit
export SSL_PRIVATE_KEY_PASSWORD=changeit
export SSL_KEY_ALIAS=myapplication
./gradlew :server:run
```

Поддерживаются `PKCS12` и `JKS` через `SSL_KEYSTORE_TYPE`, по умолчанию используется `PKCS12`.

Если сертификат выписан не на домен, а на IP-адрес `195.46.171.236`, этот IP должен быть указан в SAN сертификата, иначе Android отклонит HTTPS-соединение.

Пример конвертации существующих `fullchain.pem` и `privkey.pem` в PKCS12:

```bash
mkdir -p certs

openssl pkcs12 -export \
  -in fullchain.pem \
  -inkey privkey.pem \
  -out certs/server-keystore.p12 \
  -name myapplication \
  -passout pass:changeit
```

Docker Compose:

```bash
docker compose up -d --build server
```

`docker-compose.yml` теперь автоматически включает HTTPS для обычного `server`, если файл `certs/server-keystore.p12` существует. По умолчанию ожидаются:

- alias: `myapplication`
- пароль keystore: `changeit`
- пароль приватного ключа: `changeit`

Если у keystore другие параметры, перед запуском переопредели env:

```bash
export SSL_KEYSTORE_PATH=/certs/server-keystore.p12
export SSL_KEYSTORE_PASSWORD=your-password
export SSL_PRIVATE_KEY_PASSWORD=your-password
export SSL_KEY_ALIAS=your-alias
docker compose up -d --build server
```

Клиент Android:

```bash
./gradlew :composeApp:assembleDebug -PSERVER_BASE_URL=https://195.46.171.236:9878
```

Если `SERVER_BASE_URL` не передан, приложение использует `https://195.46.171.236:9878`.

Проверка через Docker Compose:

```bash
docker compose --profile testing run --rm server-test
docker compose --profile testing up --build https-smoke-test
```

Второй сценарий поднимает PostgreSQL, генерирует временный PKCS12 keystore внутри контейнера и проверяет, что сервер отвечает по `https://server-https-test:9878/`.
