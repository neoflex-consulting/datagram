# Datagram

__bd_runtime__ docker-compose.yml will start generate docker container with preinstalled Livy, HDFS and PostgreSQL
__Ports:__ PostgreSQL 5432 (internal) 5532 (external), user/pass see in docker compose, Livy at http://localhost:8998

## Быстрый запуск Datagram
### Требования к компьютеру
Минимальное количество памяти компьютера - 16ГБ, т.к. запускается не только сам datagram, но и его рантайм,
фактически, локальный кластер hadoop.

Если вы на Windows, убедитесь, что у вас установлен и настроен [Docker Desktop](https://www.docker.com/products/docker-desktop).
По умолчанию, ему выделяется мало памяти, увеличьте это значение в настройках Docker Desktop.
На Linux должен быть установлен docker-compose.
Убедитесь, что у вас установлен git, и на Windows лучше отключить трансляцию CRLF-LF командой
```
git config --global core.autocrlf false
```
### Сборка и запуск
```
git clone https://github.com/neoflex-ru/datagram.git
cd datagram
mvn -f pom3.xml package
docker-compose -f bd-runtime/docker-compose.yml pull
docker-compose -f bd-runtime/docker-compose.yml up -d
```

### Проверка работоспособности
Откройте начальную страницу [__Datagram__](http://localhost:8089/) в браузере.

__Логин__/__пароль__: admin/admin


