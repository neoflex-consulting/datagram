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

ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT
ENV LIVY_HOME /usr/livy
ENV JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk
ENV PATH=${PATH}:${JAVA_HOME}/bin:${LIVY_HOME}/bin

COPY --from=builder $LIVY_HOME $LIVY_HOME
COPY --from=builder /scripts /scripts

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

HEALTHCHECK CMD curl -f "http://host.docker.internal:${LIVY_PORT}/" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
