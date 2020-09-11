
angular.module('logViewer.controllers')
.controller("LogCtrl", function($scope, $log, $http, $timeout, $location, $anchorScroll, uiGridConstants) {

        $scope.logs = [];
        $scope.dataLen = 0;
        $scope.data = "";
        $scope.glued = true;
        $scope.logNum = 0;

        $scope.gridOptions = {
            data: [],
            enableFiltering: true,
            enableRowSelection: true,
            multiSelect: false,
            enableRowHeaderSelection: false
        };

        var logLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];
        $scope.gridOptions.columnDefs = [
            { name:'datetime', width:200 },
            { name:'logLevel', width:120, filter: {
                term: '2',
                type: uiGridConstants.filter.SELECT,
                selectOptions: [ { value: '0', label: logLevels[0] }, { value: '1', label: logLevels[1] }, { value: '2', label: logLevels[2]}, { value: '3', label: logLevels[3] }, { value: '4', label: logLevels[4] } ],
                condition: function(searchTerm, cellValue) { 
                    return searchTerm <= logLevels.indexOf(cellValue) ;
                }
                } 
            }
        ];

        var logScrolling = true;
        scrollTo = function(hash) {
            $location.hash(hash);
            $anchorScroll();
        };

        $scope.selectRow = function(log) {
            $scope.gridApi.core.scrollTo(log);
            logScrolling = false;
            $scope.gridApi.selection.selectRow(log);
            logScrolling = true;
        }

        $scope.gridOptions.onRegisterApi = function(gridApi){
          $scope.gridApi = gridApi;
          gridApi.selection.on.rowSelectionChanged($scope,function(row){
            if (logScrolling) {
                $scope.glued = false;
                scrollTo("log" + row.entity.num);
            }
          });
        };

        var eventRE = /(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}.\d{3})\s+\[(\S*)\]\s+\[(\S*)\]\s+([A-Z]+)\s+(\d+)\s+---\s+\[(\S*)\]\s+(\S+)\s+:\s+/g;
        function parseEvents(text) {
            if ($scope.gridOptions.data.length > 0) {
                var lastEvent = $scope.gridOptions.data.pop();
                var head = lastEvent.message
                lastEvent.message = "";
                lastEvent.text = lastEvent.text.substring(0, lastEvent.text.length - head.length);
                $scope.gridOptions.data.push(lastEvent);
                text = head + text;
            }
            var result;
            var start = 0;
            while (result = eventRE.exec(text)) {
                var event = {
                    "num"       : $scope.logNum,
                    "startIndex": result.index,
                    "endIndex"  : eventRE.lastIndex,
                    "text"      : result[0],
                    "datetime"  : result[1],
                    "ip"        : result[2],
                    "user"      : result[3],
                    "logLevel"  : result[4],
                    "process"   : result[5],
                    "thread"    : result[6],
                    "logger"    : result[7],
                    "message"   : ''   
                };
                $scope.logNum += 1;
                if ($scope.gridOptions.data.length > 0) {
                    var lastEvent = $scope.gridOptions.data.pop();
                    var tail = text.substring(start, event.startIndex);
                    lastEvent.message += tail;
                    lastEvent.text += tail;
                    $scope.gridOptions.data.push(lastEvent);
                }

                $scope.gridOptions.data.push(event);
                $scope.logs.push(event);
                start = eventRE.lastIndex;
            }

            if ($scope.gridOptions.data.length > 0) {
                var lastEvent = $scope.gridOptions.data.pop();
                var tail = text.substring(start, text.length/* - 1*/);
                lastEvent.message += tail;
                lastEvent.text += tail;
                $scope.gridOptions.data.push(lastEvent);
            }
        }

        function parse() {
            var toParse = $scope.data.substring(0, 1000000)
            parseEvents(toParse)
            $scope.data = $scope.data.substring(1000000)
            if ($scope.data.length > 0) {
                $scope.parsePromise = $timeout(parse, 10);
            }
        }

        function tail() {
            var range = "bytes=" + $scope.dataLen + "-";
            console.log(range);
            $http({
                url: $scope.host + '/logfile',
                method: 'GET',
                headers: {
                    'Range': range, 'Accept': '*/*',
                    'Cache-Control': 'no-cache, no-store, must-revalidate',
                    'Pragma': 'no-cache',
                    'Expires': '0'
                }
            }).then(function (res) {
                var timeout = 1000;

                if (res.data.length > 1) {
                    $scope.dataLen += res.data.length;
                    $scope.data += res.data;
                    $timeout.cancel($scope.parsePromise);
                    $scope.parsePromise = $timeout(parse, 10);
                    timeout = 1;
                }

                $scope.tailPromise = $timeout(tail, timeout);
            }, function (res) {
                $scope.tailPromise = $timeout(tail, 1000);
            });
        }

        function config() {
            $http({
                url: 'config.json',
                method: 'GET'
            }).then(function (res) {
                $scope.host = res.data.host;
                $scope.tailPromise = $timeout(tail, 10);
            }, function (res) {
                $timeout(config, 1000);
            });
        }

        $scope.refresh = function () {
            $scope.gridOptions.data.length = 0;
            $scope.logs.length = 0;
            $scope.dataLen = 0;
            $scope.data = "";
            $timeout.cancel($scope.tailPromise);
            $scope.tailPromise = $timeout(tail, 10);
        }

        config();

    });