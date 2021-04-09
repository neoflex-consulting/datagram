#FROM bigdata-docker-compose_master:latest
FROM neoflexdatagram/bd-base as builder

ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT
ENV LIVY_HOME /usr/livy
RUN apk add --no-cache git maven \
  && git clone https://github.com/apache/incubator-livy.git \
  && cd incubator-livy \
  && mvn package -DskipTests \
  && unzip -qq "./assembly/target/apache-livy-${LIVY_VERSION}-bin.zip" -d /usr \
  && mv "/usr/apache-livy-${LIVY_VERSION}-bin" "${LIVY_HOME}" \
  && rm -rf "./apache-livy-${LIVY_VERSION}-bin.zip" \
  && cd .. \
  && rm -rf "/incubator-livy" \
  && rm -rf "/root/.m2" \
  && mkdir "${LIVY_HOME}/logs" \
  && chown -R root:root "${LIVY_HOME}"

FROM alpine:3.10.4
RUN apk add --no-cache openjdk8 bash coreutils findutils

ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV HADOOP_HOME=/usr/hadoop
ENV HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
ENV HIVE_HOME=/usr/hive
ENV HIVE_CONF_DIR="${HIVE_HOME}/conf"
ENV HADOOP_CLASSPATH=${HADOOP_HOME}/share/hadoop/common/*:${HADOOP_HOME}/share/hadoop/tools/lib/*::${HIVE_HOME}/lib/*
ENV SPARK_HOME=/usr/spark
ENV SPARK_CONF_DIR="${SPARK_HOME}/conf"
ENV SPARK_LOG_DIR="${SPARK_HOME}/logs"
ENV SPARK_DIST_CLASSPATH="${HADOOP_CONF_DIR}:${HADOOP_HOME}/share/hadoop/tools/lib/*:${HADOOP_HOME}/share/hadoop/common/lib/*:${HADOOP_HOME}/share/hadoop/common/*:${HADOOP_HOME}/share/hadoop/hdfs:${HADOOP_HOME}/share/hadoop/hdfs/lib/*:${HADOOP_HOME}/share/hadoop/hdfs/*:${HADOOP_HOME}/share/hadoop/mapreduce/lib/*:${HADOOP_HOME}/share/hadoop/mapreduce/*:${HADOOP_HOME}/share/hadoop/yarn:${HADOOP_HOME}/share/hadoop/yarn/lib/*:${HADOOP_HOME}/share/hadoop/yarn/*:$HIVE_HOME/lib/*"
ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT
ENV LIVY_HOME /usr/livy
ENV LIVY_CONF_DIR ${LIVY_HOME}/conf
ENV PATH=${PATH}:${JAVA_HOME}/bin:${LIVY_HOME}/bin:${HADOOP_HOME}/bin:${SPARK_HOME}/bin:$HIVE_HOME/bin

COPY --from=builder $HADOOP_HOME $HADOOP_HOME
COPY --from=builder $HIVE_HOME $HIVE_HOME
COPY --from=builder $SPARK_HOME $SPARK_HOME
COPY --from=builder $LIVY_HOME $LIVY_HOME
COPY --from=builder /scripts /scripts

#RUN ln -s $SPARK_HOME/jars/scala-library-*.jar $HIVE_HOME/lib \
#    && ln -s $SPARK_HOME/jars/spark-core_*.jar $HIVE_HOME/lib \
#    && ln -s $SPARK_HOME/jars/spark-network-common_*.jar $HIVE_HOME/lib

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

HEALTHCHECK CMD curl -f "http://host.docker.internal:${LIVY_PORT}/" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
