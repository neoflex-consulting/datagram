#!/bin/bash

hdfs dfs -test -e /user/root/share/lib
if [ $? -ne 0 ]
then
  $OOZIE_HOME/bin/oozie-setup.sh sharelib create -fs hdfs://master:9000 -locallib $OOZIE_HOME/sharelib
fi
$OOZIE_HOME/bin/oozie-setup.sh db create -run
$OOZIE_HOME/bin/oozied.sh start
/scripts/parallel_commands.sh "/scripts/watchdir $OOZIE_HOME/logs"
$OOZIE_HOME/bin/oozied.sh stop
