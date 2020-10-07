#!/bin/bash

$OOZIE_HOME/bin/oozie-setup.sh sharelib create -fs ? -locallib $OOZIE_HOME/sharelib
$OOZIE_HOME/bin/oozie-setup.sh db create -run
$OOZIE_HOME/bin/oozied.sh start
/scripts/parallel_commands.sh "/scripts/watchdir $OOZIE_HOME/logs"
$OOZIE_HOME/bin/oozied.sh stop
