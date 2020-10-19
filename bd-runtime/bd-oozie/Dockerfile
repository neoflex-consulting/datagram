FROM neoflexdatagram/bd-base

ENV OOZIE_VERSION   5.2.0
ENV OOZIE_HOME      /usr/oozie
RUN apk add --no-cache git maven \
  && curl --progress-bar -L --retry 3 \
  "http://archive.apache.org/dist/oozie/${OOZIE_VERSION}/oozie-${OOZIE_VERSION}.tar.gz" \
  | gunzip \
  | tar -x -C /usr/ \
 && cd "/usr/oozie-${OOZIE_VERSION}" \
 && mkdir /usr/oozie-${OOZIE_VERSION}/core/target/ \
 && touch /usr/oozie-${OOZIE_VERSION}/core/target/oozie-core-${OOZIE_VERSION}-tests.jar \
 && mvn package assembly:single -Phadoop-3 -Dhadoop.version=$HADOOP_VERSION -Dmaven.test.skip=true -DskipTests \
 && mv "/usr/oozie-${OOZIE_VERSION}/distro/target/oozie-${OOZIE_VERSION}-distro/oozie-${OOZIE_VERSION}/" "${OOZIE_HOME}" \
 && rm -rf "/usr/oozie-${OOZIE_VERSION}" \
 && chown -R root:root "${OOZIE_HOME}" \
 && mkdir "${OOZIE_HOME}/libext" && cd "${OOZIE_HOME}/libext" \
 && curl --progress-bar -L --retry 3 -O "https://jdbc.postgresql.org/download/postgresql-42.2.16.jar" \
 && curl --progress-bar -L --retry 3 -O "http://archive.cloudera.com/gplextras/misc/ext-2.2.zip" \
 && cp $HADOOP_HOME/share/hadoop/common/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/common/lib/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/hdfs/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/hdfs/lib/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/mapreduce/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/mapreduce/lib/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/yarn/*.jar $OOZIE_HOME/libext/ \
 && cp $HADOOP_HOME/share/hadoop/yarn/lib/*.jar $OOZIE_HOME/libext/ \
 && $OOZIE_HOME/bin/oozie-setup.sh \
 && rm -rf $OOZIE_HOME/lib/jackson-*2.6.5* \
 && cd /tmp && tar xzf "$OOZIE_HOME/oozie-sharelib-${OOZIE_VERSION}.tar.gz" \
 && mv /tmp/share "${OOZIE_HOME}/sharelib" \
 && mkdir "${OOZIE_HOME}/sharelib/lib/spark2" \
 && cp  $SPARK_HOME/jars/*.jar "${OOZIE_HOME}/sharelib/lib/spark2/" \
 && cp "${OOZIE_HOME}/sharelib/lib/spark/oozie-sharelib-spark-5.2.0.jar" "${OOZIE_HOME}/sharelib/lib/spark2/" \
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
 && rm -rf /root/.m2/repository/ \
 && rm "/usr/oozie/oozie-sharelib-${OOZIE_VERSION}.tar.gz"
ADD conf/oozie/oozie-site.xml $OOZIE_HOME/conf/.
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
WORKDIR $OOZIE_HOME
EXPOSE 11000 11000
HEALTHCHECK CMD curl -f "http://host.docker.internal:11000/" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
