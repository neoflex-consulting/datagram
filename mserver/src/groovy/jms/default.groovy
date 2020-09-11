package jms

import ru.neoflex.meta.model.Database
import ru.neoflex.meta.utils.JSONHelper
import java.util.*
import ru.neoflex.meta.utils.Context

def message = this.binding.variables.message
def parameters = this.binding.variables.parameters
def id = message.id
def status = message.status
def obj = new URL(parameters.oozieBase + id)
def con = obj.openConnection()
con.setRequestMethod("GET")

def dbname = "teneo"
def db = new Database(dbname)

def responseCode = con.getResponseCode()
print "Response Code : " + responseCode + "\n"
def response = readInputStream(con.getInputStream())
//print response

//get error log
def objE = new URL(parameters.oozieBase2 + id + "?show=errorlog")
def conE = objE.openConnection()
conE.setRequestMethod("GET")
def responseCodeErrorLog = con.getResponseCode()
def responseErrorLog = readInputStream(conE.getInputStream())
print "error log: " + responseErrorLog 

def map = JSONHelper.string2map(response)
//print map

def type = "etlrt.WorkflowJob"
def job = JSONHelper.fromJSON(dbname, type, map);
job.conf = job.conf.substring(0, job.conf.length() < 4000 ? job.conf.length() : 4000)

def existingJob = null
def existingJobList = db.list(type, [id: job.id])
def timestamp = message.timestamp == null ? JSONHelper.formatDate(new Date(message.startTime)) : message.timestamp
def event = [id: UUID.randomUUID().toString(), emitterType: "WORKFLOW", emitterId: id, timestamp: timestamp, eventType: status, transformationName: "default", parentId: id, emitterSubType: "DRIVER", workflowId: id, message: responseErrorLog.substring(0, responseErrorLog.length() < 4000 ? responseErrorLog.length() : 4000)]
def eEvent = JSONHelper.fromJSON(dbname, "etlrt.RuntimeEvent", event)

db.save("etlrt.RuntimeEvent", eEvent)

if (!existingJobList.empty) {
    existingJob = existingJobList.first()
}

if(existingJob != null){
	existingJob.endTime = job.endTime
	existingJob.status = job.status
	existingJob.lastModTime = job.lastModTime
	existingJob.externalId = job.externalId
	existingJob.appName = job.appName
	existingJob.appPath = job.appPath
	existingJob.user = job.user
	existingJob.createdTime
	if(existingJob.startTime == null){
		existingJob.startTime = job.startTime
	}
	existingJob.consoleUrl = job.consoleUrl
	existingJob.conf = job.conf
	existingJob.transition = job.transition
	existingJob.run = job.run
	existingJob.acl = job.acl
	existingJob.parentId = job.parentId
	existingJob.lastModTime = job.lastModTime
	
	def needAddToActions = [];
	
	for(newAction in job.actions){		
		def updated = false; 
		for(existingAction in existingJob.actions){		
			if(existingAction.id == newAction.id){
				existingAction.endTime = newAction.endTime
				existingAction.status = newAction.status
				existingAction.errorCode = newAction.errorCode
				existingAction.errorMessage = newAction.errorMessage
				existingAction.retries = newAction.retries
				existingAction.userRetryMax = newAction.userRetryMax
				updated = true	
			}
		}
		if(updated == false){
			needAddToActions.add(newAction)
		}
	}
	if(!needAddToActions.empty){
		if(existingJob.actions == null){
			existingJob.actions = needAddToActions;
		} else {
			existingJob.actions.addAll(needAddToActions);
		}
	}
	db.save(type, existingJob)
	Context.current.contextSvc.onEvent(existingJob)
} else {
	db.save(type, job)
	Context.current.contextSvc.onEvent(job)
}

def readInputStream(is) {
    def inr = new BufferedReader(new InputStreamReader(is))
    def response = new StringBuffer()
    while (true) {
        def inputLine = inr.readLine()
        if (inputLine == null)
            break;
        response.append(inputLine)
    }
    inr.close()
    return response.toString()
}