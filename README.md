# Datagram

## Быстрый запуск Datagram
### Требования к компьютеру
Минимальное количество памяти компьютера - 32ГБ, 
т.к. запускается не только сам datagram, но и его рантайм,
фактически, локальный кластер hadoop.

Если вы на Windows, убедитесь, что у вас установлен и настроен [Docker Desktop](https://www.docker.com/products/docker-desktop).
По умолчанию, ему выделяется мало памяти, увеличьте это значение (до 8ГБ) в настройках Docker Desktop.
На Windows разрешите шарить файлы из того каталога, куда устанавливается datagram (Docker Desktop/Settings/Resources/File Sharing).
На Linux должен быть установлен docker-compose.

Убедитесь, что у вас установлен git, и на Windows лучше отключить трансляцию CRLF-LF командой
```
git config --global core.autocrlf false
```
Убедитесь, что у вас установлен maven и работает команда mvn.
### Сборка и запуск
```
git clone https://github.com/neoflex-consulting/datagram.git
cd datagram
mvn clean install 
По умолчанию используется профайл для spark3, если нужна сборка для spark2 добавить к строке запуска -Pspark2
docker-compose -f bd-runtime/docker-compose.yml pull
docker-compose -f bd-runtime/docker-compose.yml up -d
```

### Проверка работоспособности
Проверить логи программы можно командой:
```
docker-compose -f bd-runtime/docker-compose.yml logs datagram
```
__ИЛИ__ в файле `./bd-runtime/datagram/logs/mserver.log`.

В логе не должно быть сообщений об ошибках, программа стартовала успешно,
если в логе появилась запись:
```
Started MServerConfiguration in 80.091 seconds (JVM running for 91.57)
```

Откройте начальную страницу [__Datagram__](http://localhost:8089/) в браузере.

__Логин__/__пароль__: admin/admin

В интерфейсе программы перейдите в пункт `ETL/ Project/' создайте (кнопка __+__) проект __blueprint__
сохраните его и выполните команду (молния вверху) Import Project.

Откройте трансформацию  `ETL/ Transformation/ tr_load_datagram_transformations`,
эта тестовая трансформация делает выгрузку списка трансформаций из BD метаданных datagram.
Запустите её на исполнение (кнопка Run внизу и ещё раз Run в открывшемся окошке).

Для отслеживания процесса сборки/выполнения откройте панель логов (кнопка View Logs внизу)

Для просмотра кода трансформации откройте Source Code Editor ([+] справа вверху)

Для просмотра логов Livy кликните на bd-livy (слева, в панели ссылок), лог выполнения на закладке Batches.

Для просмотра содержимого hdfs откройте HDFS Console ([+] справа вверху). 
Должен появиться новый каталог `/temp/transformations` с содержимым выгрузки.

Для получения maven проекта по сборке трансформации (для использования в процессах CI/CD)
выполните команду (логин/пароль: admin/admin)
```
git clone http://localhost:8089/git/default
```
__ИЛИ__, в каталоге `./bd-runtime/datagram/gitflow/default` выполнить команду
```
git reset --hard
```
## Ссылки на WEB UI
Ресурс|URL
------|---
Datagram|http://localhost:8089/
Yarn|http://localhost:8088/
Livy|http://localhost:8998/
Oozie|http://localhost:11000/
Spark|http://localhost:8080/
Name Node|http://localhost:9870/


Для просмотра логов кластера, в файл `C:\Windows\System32\drivers\etc\hosts ` (или `/etc/hosts`) нужно добавить следующие строки:
```
127.0.0.1 master
127.0.0.1 worker1
127.0.0.1 worker2
127.0.0.1 livy
```
