FROM maven:3.6.3-openjdk-8-slim as builder
RUN apt-get update && apt-get install -y git \
  && git clone https://github.com/neoflex-consulting/datagram.git \
  && cd datagram \
  && mvn clean install

FROM maven:3.6.3-openjdk-8-slim

ENV MAVEN_HOME=/usr/share/maven
ENV MEM_MAX=6g
ENV TENEO_URL jdbc:postgresql://hivemetastore:5432/teneo
ENV TENEO_USER postgres
ENV TENEO_PASSWORD new_password
ENV DATAGRAM_HOME /opt/datagram
ENV DEPLOY_DIR $DATAGRAM_HOME/mspace
ENV VERSION spark3-2.0.0-SNAPSHOT
ENV SERVER_PORT 8089

COPY --from=builder /datagram/mserver/target/mserver-$VERSION.jar /opt/datagram/mserver.jar
COPY --from=builder /datagram/spark2lib/target/ru.neoflex.meta.etl2.spark.spark2lib-$VERSION.jar /root/.m2/repository/ru/neoflex/meta/etl2/ru.neoflex.meta.etl2.spark.spark2lib/$VERSION/ru.neoflex.meta.etl2.spark.spark2lib-$VERSION.jar
COPY --from=builder /datagram/spark2lib/.flattened-pom.xml /root/.m2/repository/ru/neoflex/meta/etl2/ru.neoflex.meta.etl2.spark.spark2lib/$VERSION/ru.neoflex.meta.etl2.spark.spark2lib-$VERSION.pom
COPY --from=builder /datagram/runtime/target/ru.neoflex.meta.etl.spark.runtime-$VERSION.jar /root/.m2/repository/ru/neoflex/meta/etl/ru.neoflex.meta.etl.spark.runtime/$VERSION/ru.neoflex.meta.etl.spark.runtime-$VERSION.jar
COPY --from=builder /datagram/runtime/.flattened-pom.xml /root/.m2/repository/ru/neoflex/meta/etl/ru.neoflex.meta.etl.spark.runtime/$VERSION/ru.neoflex.meta.etl.spark.runtime-$VERSION.pom
COPY --from=builder /datagram/.flattened-pom.xml /root/.m2/repository/ru/neoflex/parent/$VERSION/parent-$VERSION.pom

WORKDIR $DATAGRAM_HOME
RUN echo "#!/bin/bash\njava -Xmx${MEM_MAX} -Dteneo.url=${TENEO_URL} -Dteneo.user=${TENEO_USER} -Dteneo.password=${TENEO_PASSWORD} -Dmaven.home=${MAVEN_HOME} -Ddeploy.dir=${DEPLOY_DIR} -Dserver.port=${SERVER_PORT} -Dfile.encoding=UTF-8 -jar mserver.jar" >./entrypoint.sh
RUN chmod +x ./entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
