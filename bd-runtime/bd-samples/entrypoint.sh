#!/bin/bash

hdfs dfs -test -e /samples
if [ $? -ne 0 ]
then
  hdfs dfs -put /data/samples /
fi
