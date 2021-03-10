FROM neoflexdatagram/bd-base

ENV PGPASSWORD=new_password

RUN apk add --no-cache postgresql-client git ruby jq

COPY data /data
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
