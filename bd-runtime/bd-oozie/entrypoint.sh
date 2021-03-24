#!/bin/bash

export LD_BIND_NOW=1

echo "Find jarhell in $OOZIE_HOME/embedded-oozie-server/webapp/WEB-INF/lib:$OOZIE_HOME/libext"
python3 /jarhell.py "$OOZIE_HOME/embedded-oozie-server/webapp/WEB-INF/lib:$OOZIE_HOME/libext"

hdfs dfs -test -e /user/root/share/lib
if [ $? -ne 0 ]
then
  cp "${OOZIE_HOME}/sharelib/lib/spark/oozie-sharelib-spark-5.2.0.jar" "${OOZIE_HOME}/sharelib/lib/spark2/" \
   && cp  $SPARK_HOME/jars/*.jar "${OOZIE_HOME}/sharelib/lib/spark2/" \
   && cp $HADOOP_HOME/share/hadoop/common/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/common/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/hdfs/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/hdfs/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/mapreduce/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/mapreduce/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/yarn/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/yarn/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HADOOP_HOME/share/hadoop/yarn/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp "${HADOOP_CONF_DIR}/core-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp "${HADOOP_CONF_DIR}/hdfs-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp "${HADOOP_CONF_DIR}/mapred-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp "${HADOOP_CONF_DIR}/yarn-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
   && cp $HIVE_HOME/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/
  python3 /jarhell.py "$OOZIE_HOME/sharelib/lib/spark2"
  $OOZIE_HOME/bin/oozie-setup.sh sharelib create -fs hdfs://master:9000 -locallib $OOZIE_HOME/sharelib
fi

$OOZIE_HOME/bin/oozie-setup.sh db create -run

$OOZIE_HOME/bin/oozied.sh start
/scripts/parallel_commands.sh "/scripts/watchdir $OOZIE_HOME/logs"
$OOZIE_HOME/bin/oozied.sh stop
