#!/bin/bash

hdfs dfs -test -e /samples
if [ $? -ne 0 ]
then
  hdfs dfs -put /data/samples /
fi

cnt=`psql -h hivemetastore -U postgres -c "SELECT datname FROM pg_database WHERE datname='Adventureworks'" -qt | wc -l`
if [ $cnt -le 1 ]
then
  git clone https://github.com/lorint/AdventureWorks-for-Postgres.git && \
    cd AdventureWorks-for-Postgres && \
    mkdir data && cd data && \
    wget https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/AdventureWorks-oltp-install-script.zip && \
    unzip AdventureWorks-oltp-install-script.zip && rm AdventureWorks-oltp-install-script.zip && \
    ruby ../update_csvs.rb && cp ../install.sql ./ && \
    psql -h hivemetastore -U postgres -c 'CREATE DATABASE "Adventureworks"' && \
    psql -h hivemetastore -d Adventureworks -U postgres <install.sql
fi

curl -s --user admin:admin http://datagram:8089/info
while [ $? -ne 0 ]
do
  echo "Waiting for datagram..."
  sleep 1
  curl -s --user admin:admin http://datagram:8089/info
done

echo "\nCheck for projects"
projectsFound=`curl -s --user admin:admin http://datagram:8089/api/teneo/etl.Project | jq 'length>0'`
echo "\nProjects found: $projectsFound"
if [ $projectsFound != 'true' ]
then
  echo "No projects found. Creating blueprint"
  project_id=`curl -s --user admin:admin --request POST --header "Content-Type: application/json" --data '{"_type_":"etl.Project", "name":"blueprint"}' http://datagram:8089/api/teneo/etl.Project | jq '.e_id'`;
  echo "New project: $project_id. Import project.";
  imported=`curl -s --user admin:admin http://datagram:8089/api/operation/MetaServer/etl/Project/blueprint/importProject | jq 'length'`;
  echo "Imported objects: $imported";
fi

cd /kafka
ping -c1 kafka
if [ $? -eq 0 ]
then
  echo "Kafka found. Check for topics"
  topicCount=`bin/kafka-topics.sh --list --bootstrap-server kafka:9092 | grep '^events$' | wc -l`
  if [ $topicCount -eq 0 ]
  then
    echo "No topics found. Create topic 'events' and send some data"
    bin/kafka-topics.sh --create --topic events --bootstrap-server kafka:9092
    bin/kafka-console-producer.sh --topic events --bootstrap-server kafka:9092 <<EOF
{"id": 1, "name":"Oleg"}
{"id": 2, "name":"Maksim"}
{"id": 3, "name":"Anna"}
EOF
  fi
fi

echo "All done. Waiting forever to allow exec command."
tail -f /dev/null