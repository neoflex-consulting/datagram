FROM neoflexdatagram/bd-base as builder

FROM alpine:3.10.4

ENV PGPASSWORD=new_password
ENV HADOOP_HOME=/usr/hadoop
ENV HADOOP_CONF_DIR="${HADOOP_HOME}/etc/hadoop"
ENV PATH="${PATH}:${HADOOP_HOME}/bin"
ENV JAVA_HOME "/usr/lib/jvm/java-1.8-openjdk"


RUN apk add --no-cache postgresql-client git ruby jq bash openjdk8 curl
RUN wget https://downloads.apache.org/kafka/2.7.0/kafka_2.13-2.7.0.tgz \
  && tar -xzf kafka_2.13-2.7.0.tgz \
  && mv kafka_2.13-2.7.0 kafka \
  && rm -f kafka_2.13-2.7.0.tgz \
  && git clone https://github.com/lorint/AdventureWorks-for-Postgres.git \
  && wget https://github.com/Microsoft/sql-server-samples/releases/download/adventureworks/AdventureWorks-oltp-install-script.zip \
  && unzip AdventureWorks-oltp-install-script.zip -d AdventureWorks-for-Postgres && rm AdventureWorks-oltp-install-script.zip \
  && cd AdventureWorks-for-Postgres && ruby update_csvs.rb

COPY --from=builder $HADOOP_HOME $HADOOP_HOME
COPY data /data
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
