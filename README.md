# Datagram

__bd_runtime__ docker-compose.yml will start generate docker container with preinstalled Livy, HDFS and PostgreSQL
__Ports:__ PostgreSQL 5432 (internal) 5532 (external), user/pass see in docker compose, Livy at http://localhost:8998

```
git config --global core.autocrlf false
git clone https://github.com/neoflex-ru/datagram.git
cd datagram
mvn -f pom3.xml package
docker-compose -f bd-runtime/docker-compose.yml pull
docker-compose -f bd-runtime/docker-compose.yml up -d
```

Point browser to `http://localhost:8998/` to check Livy Console.


