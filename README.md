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
