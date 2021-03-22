FROM openjdk:8-jdk-alpine

ENV MAVEN_HOME=/usr/share/maven
ENV MEM_MAX=6g
ENV TENEO_URL jdbc:postgresql://hivemetastore:5432/teneo
ENV TENEO_USER postgres
ENV TENEO_PASSWORD new_password
ENV DEPLOY_DIR /opt/datagram/mspace

RUN apk --no-cache add git bash

RUN cd /tmp \
   && wget https://archive.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz \
   && tar zxf apache-maven-3.3.9-bin.tar.gz \
   && rm -rf apache-maven-3.3.9-bin.tar.gz \
   && mv ./apache-maven-3.3.9 /usr/share/maven \
   && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
WORKDIR /opt/datagram
ENTRYPOINT ["/entrypoint.sh"]
