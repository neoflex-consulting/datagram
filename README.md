# Datagram

## Быстрый запуск Datagram
### Требования к компьютеру
Минимальное количество памяти компьютера - 16ГБ, т.к. запускается не только сам datagram, но и его рантайм,
фактически, локальный кластер hadoop.

Если вы на Windows, убедитесь, что у вас установлен и настроен [Docker Desktop](https://www.docker.com/products/docker-desktop).
По умолчанию, ему выделяется мало памяти, увеличьте это значение в настройках Docker Desktop.
На Windows разрешите шарить файлы из каталога, куда устанавливается datagram (Docker Desktop/Settings/Resources/File Sharing).
На Linux должен быть установлен docker-compose.

Убедитесь, что у вас установлен git, и на Windows лучше отключить трансляцию CRLF-LF командой

Убедитесь, что у вас установлен maven и работает команда mvn.
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
Проверить логи программы можно командой:
```
docker-compose -f bd-runtime/docker-compose.yml logs datagram
```
В логе не должно быть сообщений об ошибках, программа стартовала успешно,
если в логе появилась запись:
```
Started MServerConfiguration in 80.091 seconds (JVM running for 91.57)
```

Откройте начальную страницу [__Datagram__](http://localhost:8089/) в браузере.

__Логин__/__пароль__: admin/admin

В интерфейсе программы перейдите в проект blueprint ETL/ Project/ blueprint,
и выполните команду (молния вверху) Import Repo.

Откройте трансформацию  ETL/ Transformation/ tr_load_datagram_transformations
и запустите её на исполнение (Run внизу и ещё раз Run в открывшемся окошке).

Для отслеживания процесса сборки/выполнения откройте панель логов (кнопка View Logs внизу)



### Ссылки на WEB UI
Ресурс|URL
------|---
Datagram|http://localhost:8089/
Yarn|http://localhost:8088/
Livy|http://localhost:8998/
Spark|http://localhost:8080/

