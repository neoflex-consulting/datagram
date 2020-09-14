#!/bin/bash

/usr/bin/java -Xms2g -Xmx6g -Dfile.encoding=UTF-8 -Dldap.enabled=false -Dldap.always_admin=true -Dmaven.home=/usr/share/java/maven-3 \
  -Ddeploy.dir=/opt/datagram/mspace -Dteneo.url=jdbc:postgresql://hivemetastore:5432/teneo \
  -Dteneo.user=postgres -Dteneo.password=new_password -Dserver.port=8089 \
  -jar /opt/datagram/mserver-2.0-SNAPSHOT.jar