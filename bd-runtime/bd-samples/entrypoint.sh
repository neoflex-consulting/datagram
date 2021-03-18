#!/bin/bash

echo "Check for hdfs /samples catalog"
hdfs dfs -test -e /samples &>/dev/null
if [ $? -ne 0 ]
then
  echo "/samples catalog not found. Uploading..."
  hdfs dfs -put /data/samples /
fi

echo "Check for Adventureworks db"
psql -lqt -h hivemetastore -U postgres | cut -d \| -f 1 | grep -qw 'Adventureworks'
if [ $? -ne 0 ]
then
  echo "Adventureworks db not found. Creating..."
  git clone https://github.com/lorint/AdventureWorks-for-Postgres.git && \
    cd AdventureWorks-for-Postgres && \
    mkdir data && cd data && \
    wget https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/AdventureWorks-oltp-install-script.zip && \
    unzip AdventureWorks-oltp-install-script.zip && rm AdventureWorks-oltp-install-script.zip && \
    ruby ../update_csvs.rb && cp ../install.sql ./ && \
    psql -h hivemetastore -U postgres -c 'CREATE DATABASE "Adventureworks"' && \
    psql -h hivemetastore -d Adventureworks -U postgres <install.sql
fi

curl -s --user admin:admin http://datagram:8089/info &> /dev/null
while [ $? -ne 0 ]
do
  echo "[$(date +'%T')] Waiting for datagram..."
  sleep 1
  curl -s --user admin:admin http://datagram:8089/info &> /dev/null
done

echo "Check for projects"
projectsFound=`curl -s --user admin:admin http://datagram:8089/api/teneo/etl.Project | jq 'length>0'`
echo "Projects found: $projectsFound"
if [ $projectsFound != 'true' ]
then
  echo "No projects found. Creating blueprint"
  project_id=`curl -s --user admin:admin --request POST --header "Content-Type: application/json" --data '{"_type_":"etl.Project", "name":"blueprint"}' http://datagram:8089/api/teneo/etl.Project | jq '.e_id'`;
  echo "New project id: $project_id. Importing...";
  imported=`curl -s --user admin:admin http://datagram:8089/api/operation/MetaServer/etl/Project/blueprint/importProject | jq 'length'`;
  echo "Imported objects: $imported";
fi

cd /kafka
ping -c1 kafka &>/dev/null
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