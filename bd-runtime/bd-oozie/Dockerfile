
FROM neoflexdatagram/bd-base as builder

ENV OOZIE_VERSION=5.2.0
ENV OOZIE_HOME=/usr/oozie

RUN apk add --no-cache busybox maven python3
RUN curl --progress-bar -L --retry 10 --retry-delay 3 \
    "http://archive.apache.org/dist/oozie/${OOZIE_VERSION}/oozie-${OOZIE_VERSION}.tar.gz" \
        | gunzip \
        | tar -x -C /usr/
RUN cd "/usr/oozie-${OOZIE_VERSION}" \
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
    && cd /tmp && tar xzf "$OOZIE_HOME/oozie-sharelib-${OOZIE_VERSION}.tar.gz" \
    && mv /tmp/share "${OOZIE_HOME}/sharelib" \
    && mkdir "${OOZIE_HOME}/sharelib/lib/spark2" \
    && rm -rf /root/.m2/repository/ \
    && rm "/usr/oozie/oozie-sharelib-${OOZIE_VERSION}.tar.gz"
RUN    cp $SPARK_HOME/jars/*.jar "${OOZIE_HOME}/sharelib/lib/spark2/" \
    && cp $HADOOP_HOME/share/hadoop/common/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/common/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/hdfs/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/hdfs/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/mapreduce/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/mapreduce/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/yarn/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HADOOP_HOME/share/hadoop/yarn/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp "${HADOOP_CONF_DIR}/core-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp "${HADOOP_CONF_DIR}/hdfs-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp "${HADOOP_CONF_DIR}/mapred-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp "${HADOOP_CONF_DIR}/yarn-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp "${HIVE_CONF_DIR}/hive-site.xml" $OOZIE_HOME/sharelib/lib/spark2/ \
    && cp $HIVE_HOME/lib/*.jar $OOZIE_HOME/sharelib/lib/spark2/ \
    && (cd $OOZIE_HOME/sharelib/lib/spark2/ && curl -O  https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.12.13/scala-compiler-2.12.13.jar) \
    && (cd $OOZIE_HOME/sharelib/lib/spark2/ && curl -O  https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.13/scala-library-2.12.13.jar) \
    && (cd $OOZIE_HOME/sharelib/lib/spark2/ && curl -O  https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.13/scala-reflect-2.12.13.jar) \
    && python3 /jarhell.py "$OOZIE_HOME/embedded-oozie-server/webapp/WEB-INF/lib:$OOZIE_HOME/libext" \
    && rm -rf /usr/oozie/sharelib/lib/spark2/hive-exec-3.1.2.jar \
    && rm -rf /usr/oozie/sharelib/lib/spark2/*netty*4.1.17* \
    && rm -rf /usr/oozie/sharelib/lib/spark2/hive-druid-handler-3.1.2.jar \
    && rm -rf /usr/oozie/sharelib/lib/spark2/antlr4-runtime-4.5.jar \
    && echo "!!!repack hive-exec!!!" \
    && unzip -q /usr/hive/lib/hive-exec-3.1.2.jar -d /hive_exec \
    && rm -rf /hive_exec/com/google \
    && rm -rf /hive_exec/org/apache/commons/lang3 \
    && cd /hive_exec && jar cf hive-exec-3.1.2.jar * \
    && mv hive-exec-3.1.2.jar /usr/oozie/sharelib/lib/spark2 \
    && cd / && rm -rf /hive_exec \
    && cp ${OOZIE_HOME}/sharelib/lib/spark/oozie-sharelib-spark-${OOZIE_VERSION}.jar ${OOZIE_HOME}/sharelib/lib/spark2

FROM alpine:3.10.4
RUN apk add --no-cache openjdk8 bash coreutils findutils python3 curl jq

ENV OOZIE_VERSION=5.2.0
ENV OOZIE_HOME=/usr/oozie
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV HADOOP_HOME=/usr/hadoop
ENV PATH=${PATH}:${JAVA_HOME}/bin:${HADOOP_HOME}/bin:${OOZIE_HOME}/bin

COPY --from=builder $OOZIE_HOME $OOZIE_HOME
COPY --from=builder $HADOOP_HOME $HADOOP_HOME
COPY --from=builder /scripts /scripts
COPY --from=builder /extralib /extralib
COPY --from=builder /jarhell.py /jarhell.py
ADD conf/oozie/oozie-site.xml $OOZIE_HOME/conf/.
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

WORKDIR $OOZIE_HOME

EXPOSE 11000 11000
HEALTHCHECK CMD curl -f "http://host.docker.internal:11000/" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
