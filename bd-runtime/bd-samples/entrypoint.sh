#!/bin/bash

echo "Check for HDFS is Up"
hdfsUp=`hdfs dfsadmin -report 2>/dev/null | grep 'Live datanodes (2)' | wc -l`
while [ $hdfsUp -ne 1 ]
do
  echo "[$(date +'%T')] Waiting for HDFS..."
  sleep 1
  hdfsUp=`hdfs dfsadmin -report 2>/dev/null | grep 'Live datanodes (2)' | wc -l`
done

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
  cd /AdventureWorks-for-Postgres
  psql -h hivemetastore -U postgres -c 'CREATE DATABASE "Adventureworks"'
  psql -h hivemetastore -d Adventureworks -U postgres <install.sql
  cd /
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
  echo "Imported objects: $imported. Install wf_load_datagram_transformations";
  installed=`curl -s --user admin:admin http://datagram:8089/api/operation/MetaServer/etl/Workflow/wf_load_datagram_transformations/install | jq '.result'`;
  echo "Installed wf_load_datagram_transformations: $installed";
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

TR_NAME=tr_aw_long_time_no_sale
URL_BASE='cim/ddesigner/build/index.html?path=/eyJfdHlwZV8iOiJ1aTMuTW9kdWxlIiwibmFtZSI6IkVUTCJ9/eyJfdHlwZV8iOiJlY29yZS5FQ2xhc3MiLCJuYW1lIjoiZXRsLlRyYW5zZm9ybWF0aW9uIn0=/'
TR_ID=`curl -s --user admin:admin "http://datagram:8089/api/teneo/select/from%20etl.Transformation%20where%20name='$TR_NAME'" | jq '.[0].e_id'`
EOBJECT="{\"_type_\":\"etl.Transformation\",\"e_id\":$TR_ID,\"name\":\"$TR_NAME\"}"
TR_URL="$URL_BASE`echo $EOBJECT | base64`"
echo $TR_URL > tr.url

echo "All done. Waiting forever to allow exec command."
tail -f /dev/null