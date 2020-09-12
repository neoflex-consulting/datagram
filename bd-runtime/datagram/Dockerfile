FROM alpine:3.10.4
RUN apk add --no-cache git maven 'openjdk8=~8.252' 'bash=~5.0'

COPY entrypoint.sh /opt/datagram/
RUN chmod +x /opt/datagram/entrypoint.sh
ENTRYPOINT ["/opt/datagram/entrypoint.sh"]