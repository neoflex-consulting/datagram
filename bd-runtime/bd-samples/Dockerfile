FROM neoflexdatagram/bd-base

COPY data /data
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
