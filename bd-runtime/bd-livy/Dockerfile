#FROM bigdata-docker-compose_master:latest
FROM neoflex/bd-base

ARG LIVY_VERSION=0.8.0-incubating-SNAPSHOT
ENV LIVY_HOME /usr/livy
ENV LIVY_CONF_DIR "${LIVY_HOME}/conf"
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

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

HEALTHCHECK CMD curl -f "http://host.docker.internal:${LIVY_PORT}/" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
