#!/bin/bash

export LD_BIND_NOW=1

echo "Check for HDFS is Up"
hdfsUp=`hdfs dfsadmin -report 2>/dev/null | grep 'Live datanodes (2)' | wc -l`
while [ $hdfsUp -ne 1 ]
do
  echo "[$(date +'%T')] Waiting for HDFS..."
  sleep 1
  hdfsUp=`hdfs dfsadmin -report 2>/dev/null | grep 'Live datanodes (2)' | wc -l`
done


hdfs dfs -test -e /user/root/share/lib
if [ $? -ne 0 ]
then
  cp /extralib/* "$OOZIE_HOME/sharelib/lib/spark2"
  python3 /jarhell.py "$OOZIE_HOME/sharelib/lib/spark2:$OOZIE_HOME/sharelib/lib/hive2:$OOZIE_HOME/sharelib/lib/oozie:$OOZIE_HOME/sharelib/lib/git:$OOZIE_HOME/sharelib/lib/pig"
  oozie-setup.sh sharelib create -fs hdfs://master:9000 -locallib $OOZIE_HOME/sharelib
fi

oozie-setup.sh db create -run

oozied.sh start

/scripts/parallel_commands.sh "/scripts/watchdir $OOZIE_HOME/logs"

oozied.sh stop
