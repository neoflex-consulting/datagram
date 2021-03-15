FROM neoflexdatagram/bd-base

ENV PGPASSWORD=new_password

RUN apk add --no-cache postgresql-client git ruby jq
RUN wget https://downloads.apache.org/kafka/2.7.0/kafka_2.13-2.7.0.tgz \
  && tar -xzf kafka_2.13-2.7.0.tgz \
  && mv kafka_2.13-2.7.0 kafka


COPY data /data
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
