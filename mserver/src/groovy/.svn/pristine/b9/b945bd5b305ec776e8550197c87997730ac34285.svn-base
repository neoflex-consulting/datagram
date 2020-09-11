package jms
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.Context

def message = this.binding.variables.message
println(message)
def dbname = "teneo"
def db = new Database(dbname)

def type = message._type_
def runtimeEvent = JSONHelper.fromJSON(dbname, type, message)
db.merge(type, runtimeEvent)

def environmentName = message.environmentName
if (environmentName == null) {
    environmentName = "default";
}
def eEnvironment = null
def environmentList = db.list("etlrt.Environment", [name: environmentName])
if (!environmentList.empty) {
    eEnvironment = environmentList.first()
}
else {
    eEnvironment = db.create("etlrt.Environment", JSONHelper.fromJSON(dbname, "etlrt.Environment", [
            "_type_": "etlrt.Environment",
            "name": environmentName
    ]))
}
def emitterType = message.emitterType

def executionId = null
if (emitterType == "EXECUTION") {
    //executionId = message.emitterId
    executionId = message.id
}
else if (emitterType == "STATISTIC") {
    executionId = message.parentId
}

def eExecution = null
def emList = db.list("etlrt.Execution", [id: executionId])

if (!emList.empty) {
    eExecution = emList.first()
    if (emitterType == "EXECUTION") {
    	if(message.eventType == 'FINISH'){
        	eExecution.finishTime = JSONHelper.parseTimestamp(message.timestamp)
        }
        eExecution.executionStatus = JSONHelper.getEnumerator(dbname, "etlrt.Execution", "executionStatus", "FINISHED")        
        eExecution.workflowId = message.workflowId
        
        if(message.name != null){
        	eExecution.name = message.name
        }
        
        eExecution.master = message.master
		eExecution.actualDate = JSONHelper.parseTimestamp(message.actualDate)
		eExecution.executionParams = []
		eExecution.contextMappings = []		
		if(message.eventType == 'START'){
			eExecution.startTime = JSONHelper.parseTimestamp(message.timestamp)
		}
		eExecution.workflowJob = findOrCreateWorkflow(message.workflowId, dbname, db)
    }
}
else {
	if(emitterType == "EXECUTION"){
    	def execution = createExecution(executionId, message)
    	eExecution = JSONHelper.fromJSON(dbname, "etlrt.Execution", execution)
    	eExecution.environment = eEnvironment
    	eExecution.workflowJob = findOrCreateWorkflow(eExecution.workflowId, dbname, db)
    	db.create("etlrt.Execution", eExecution)
    } else {
    	def execution = createEmptyExecution(executionId, message.executionStatus)
    	eExecution = JSONHelper.fromJSON(dbname, "etlrt.Execution", execution)
    	eExecution.environment = eEnvironment
    	db.create("etlrt.Execution", eExecution)
    }        
}

if (emitterType == "STATISTIC") {
    def statisticId = message.emitterId
    def eStatistic = null
    def stList = db.list("etlrt.Statistic", [id: statisticId, 'execution.id': executionId])
    if (!stList.empty) {
        eStatistic = stList.first();
        def timestamp = JSONHelper.parseTimestamp(message.timestamp)
        //if (eStatistic.lashChangeTime == null || eStatistic.lashChangeTime < timestamp) {
        //    eStatistic.lashChangeTime = timestamp
        //}
        if (message.eventType == "EXEC_UPDATE") {
            eStatistic.tuplesProcessed = eStatistic.tuplesProcessed + message.get('tuplesProcessed', 0)
            eStatistic.message = message.message
            if (message.emitterSubType == "DRIVER") {
                eStatistic.driverTimestamp = timestamp
                if (eStatistic.startTime == null) {
                    eStatistic.startTime = timestamp
                }
            }
            else if (message.emitterSubType == "WORKER") {
                if (timestamp < eStatistic.workerTimestamp || eStatistic.workerTimestamp == null) {
                    eStatistic.workerTimestamp = timestamp
                }

                if (eStatistic.workerTimestamp > eStatistic.driverTimestamp) {
                    eStatistic.startTime = eStatistic.workerTimestamp
                }
            }
            else if (message.emitterSubType == "ENVIRONMENT") {
                eStatistic.environmentTimestamp = eStatistic.creationTime
            }
            if (timestamp > eStatistic.finishTime) {
                eStatistic.finishTime = timestamp
                if (eStatistic.finishTime > eExecution.finishTime) {
                    eExecution.finishTime = eStatistic.finishTime
                }
            }
        }
        else if (message.eventType == "EXCEPTION") {
            eStatistic.tuplesFailed = eStatistic.tuplesFailed + message.get('tuplesFailed', 0)
            eStatistic.executionStatus = JSONHelper.getEnumerator(dbname, "etlrt.Statistic", "executionStatus", "FAILED")
            eExecution.executionStatus = JSONHelper.getEnumerator(dbname, "etlrt.Execution", "executionStatus", "FAILED")
        }
        else if (message.eventType == "FINISH") {
            if (eStatistic.executionStatus != JSONHelper.getEnumerator(dbname, "etlrt.Statistic", "executionStatus", "FAILED")) {
                eStatistic.executionStatus = JSONHelper.getEnumerator(dbname, "etlrt.Statistic", "executionStatus", "FINISHED")
            }
            eStatistic.finishTime = timestamp;
        }
    }
    else {
        def statistic = createStatistic(statisticId, message)
        eStatistic = JSONHelper.fromJSON(dbname, "etlrt.Statistic", statistic)
        eExecution.statistics.add(eStatistic)
        eStatistic.execution = eExecution
    }
}

db.save("etlrt.Execution", eExecution)
Context.current.contextSvc.onEvent(eExecution)

// helpers
def createExecution(id, message) {
    def executionParams = new LinkedList()
    def contextMappings = new LinkedList()
    return [
            '_type_': 'etlrt.Execution',
            'executionStatus': 'INPROGRESS',
            'name': message.transformationName,
            'master': message.master,
            'actualDate': message.actualDate,
            'id': id,
            'executionParams': executionParams,
            'contextMappings': contextMappings,
            'startTime': message.timestamp,
            'workflowId': message.workflowId
    ]
}

def createEmptyExecution(id, status) {
    def executionParams = new LinkedList()
    def contextMappings = new LinkedList()
    return [
            '_type_': 'etlrt.Execution',
            'executionStatus': status,
            'id': id,
            'name': 'default',
            'executionParams': executionParams,
            'contextMappings': contextMappings            
    ]
}


def createStatistic(id, message) {
    return [
            '_type_': 'etlrt.Statistic',
            'rddName': message.rddName,
            'message': message.message,
            'tuplesProcessed': message.get('tuplesProcessed', 0),
            'startTime': message.timestamp,
            'lashChangeTime': message.timestamp,
            'name': message.stepName,
            'tuplesFailed': message.get('tuplesFailed', 0),
            'executionStatus': message.nextStatus,
            'id': id,
            'name': message.statisticName
    ]
}

def findOrCreateWorkflow(id, dbname, db){
	def type = "etlrt.WorkflowJob"
	def existingJob = null
	def existingJobList = db.list(type, [id: id])
	if (!existingJobList.empty) {
	    return existingJobList.first()
	} else {		
		def res = JSONHelper.fromJSON(dbname, type, ['_type_': type,'id': id,'status': 'RUN'])		
		db.save(type, res)
		Context.current.contextSvc.onEvent(res)		
		return res 
	}
}
