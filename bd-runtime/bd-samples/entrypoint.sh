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

projectsLength=`curl -s --user admin:admin http://datagram:8089/api/teneo/etl.Project | jq 'length'`
echo "$projectsLength projects found"
if [ $projectsLength -eq 0 ]
then
  project_id=`curl -s --user admin:admin --request POST --header "Content-Type: application/json" --data '{"_type_":"etl.Project", "name":"blueprint"}' http://datagram:8089/api/teneo/etl.Project | jq '.e_id'`;
  echo "New project: $project_id";
  imported=`curl -s --user admin:admin http://datagram:8089/api/operation/MetaServer/etl/Project/blueprint/importProject | jq 'length'`;
  echo "Imported objects: $imported";
fi

tail -f /dev/null