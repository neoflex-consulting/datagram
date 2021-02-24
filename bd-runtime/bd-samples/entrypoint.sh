#!/bin/bash

hdfs dfs -test -e /samples
if [ $? -ne 0 ]
then
  hdfs dfs -put /data/samples /
fi

export cnt=`psql -h hivemetastore -U postgres -c "SELECT datname FROM pg_database WHERE datname='Adventureworks'" -qt | wc -l`
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


tail -f /dev/null