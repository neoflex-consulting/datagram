#!/bin/bash

/usr/bin/java -Xmx${MEM_MAX} -Dteneo.url=${TENEO_URL} -Dteneo.user=${TENEO_USER} -Dteneo.password=${TENEO_PASSWORD} \
  -Dmaven.home=${MAVEN_HOME} -Ddeploy.dir=${DEPLOY_DIR} -Dfile.encoding=UTF-8 -jar mserver.jar