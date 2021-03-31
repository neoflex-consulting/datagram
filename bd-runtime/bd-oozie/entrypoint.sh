#!/bin/bash

export LD_BIND_NOW=1

python3 /jarhell.py "$OOZIE_HOME/embedded-oozie-server/webapp/WEB-INF/lib:$OOZIE_HOME/libext"

hdfs dfs -test -e /user/root/share/lib
if [ $? -ne 0 ]
then
  rm -rf /usr/oozie/sharelib/lib/spark2/hive-exec-3.1.2.jar
  rm -rf /usr/oozie/sharelib/lib/spark2/*netty*4.1.17*
  rm -rf /usr/oozie/sharelib/lib/spark2/hive-druid-handler-3.1.2.jar
  rm -rf /usr/oozie/sharelib/lib/spark2/antlr4-runtime-4.5.jar

  unzip -q /usr/hive/lib/hive-exec-3.1.2.jar -d /hive_exec
  rm -rf /hive_exec/com/google
  cd /hive_exec && jar cf hive-exec-3.1.2.jar *
  mv hive-exec-3.1.2.jar /usr/oozie/sharelib/lib/spark2
  cd / && rm -rf /hive_exec

  python3 /jarhell.py "$OOZIE_HOME/sharelib/lib/spark2"
  $OOZIE_HOME/bin/oozie-setup.sh sharelib create -fs hdfs://master:9000 -locallib $OOZIE_HOME/sharelib
fi

$OOZIE_HOME/bin/oozie-setup.sh db create -run

$OOZIE_HOME/bin/oozied.sh start
/scripts/parallel_commands.sh "/scripts/watchdir $OOZIE_HOME/logs"
$OOZIE_HOME/bin/oozied.sh stop
