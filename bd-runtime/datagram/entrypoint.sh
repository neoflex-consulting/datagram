#!/bin/bash
cd /opt/datagram
/usr/bin/java -Xmx${MEM_MAX} -Dteneo.url=${TENEO_URL} -Dteneo.user=${TENEO_USER} -Dteneo.password=${TENEO_PASSWORD} \
  -Dfile.encoding=UTF-8 -jar mserver.jar