#!/bin/sh 
SERVICE_NAME=Datagram
export M2_HOME=/usr/share/maven;
export MAVEN_HOME=/usr/share/maven
 
##PATH_TO_JAR=/opt/datagram/mserver-2.0-SNAPSHOT.jar
PATH_TO_JAR=/opt/datagram/mserver-spark2-2.0.0-SNAPSHOT.jar
 
PID_PATH_NAME=/opt/datagram/datagram.pid 
SYS_PARAMS="-Xmx12000m -XX:MaxPermSize=1024m -Dfile.encoding=UTF-8 -Dmspace.dir=/opt/datagram/mspace -Ddeploy.dir=/opt/datagram/mspace -Dteneo.url=jdbc:postgresql://datagram_base_server:5432/db -Dteneo.user=user -Dteneo.password=password -Dcust.code=dev -Dmserver.port=8080 -Dldap.enabled=true"
case $1 in 
start)
       echo "Starting $SERVICE_NAME ..."
  if [ ! -f $PID_PATH_NAME ]; then 
       nohup  /usr/java/jdk1.8.0_202/bin/java $SYS_PARAMS -jar $PATH_TO_JAR 2>> /dev/null >>/dev/null & echo $! > $PID_PATH_NAME  
       echo "$SERVICE_NAME started ..."         
  else 
       echo "$SERVICE_NAME is already running ..."
  fi
;;
stop)
  if [ -f $PID_PATH_NAME ]; then
         PID=$(cat $PID_PATH_NAME);
         echo "$SERVICE_NAME stoping ..." 
         kill $PID;         
         echo "$SERVICE_NAME stopped ..." 
         rm $PID_PATH_NAME       
  else          
         echo "$SERVICE_NAME is not running ..."   
  fi    
;;    
restart)  
  if [ -f $PID_PATH_NAME ]; then 
      PID=$(cat $PID_PATH_NAME);    
      echo "$SERVICE_NAME stopping ..."; 
      kill $PID;           
      echo "$SERVICE_NAME stopped ...";  
      rm $PID_PATH_NAME     
      echo "$SERVICE_NAME starting ..."  
       nohup  /usr/java/jdk1.8.0_202/bin/java $SYS_PARAMS -jar $PATH_TO_JAR 2>> /dev/null >>/dev/null & echo $! > $PID_PATH_NAME  
      echo "$SERVICE_NAME started ..."    
  else           
      echo "$SERVICE_NAME is not running ..."    
     fi     ;;
 esac
