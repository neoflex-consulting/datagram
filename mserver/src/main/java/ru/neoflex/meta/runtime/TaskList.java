package ru.neoflex.meta.runtime;

import static ru.neoflex.meta.runtime.TaskList.TaskType.SPARK;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TaskList {
    public enum TaskType {WORKFLOW, COORDINATOR, COORDINATOR_ACTION, SPARK, JOBSERVER}
    public enum TaskStatus {ACTIVE, ALL}
                        
    public abstract static class Task {
        public String name;
        public String externalId;
        public String status;
        public String user;
        public String appFolder;
        public String home;
        public TaskType taskType;
        public String actionType;
        public String id;
        public String parent;
        public Date startTime;
        public Date finishTime;
        public String url;
        public String config;
        public Map<String, String> runParams = new HashMap<>();
        private SimpleDateFormat dateFormat = new SimpleDateFormat("dd-M-yyyy HH:mm:ss");        
        
		public boolean isStatus(TaskStatus status){
		    return (status == TaskStatus.ACTIVE && (this.status != null && (this.status.toUpperCase().equals("ACTIVE") || this.status.toUpperCase().equals("RUNNING") || this.status.toUpperCase().equals("PREP")))) 
		    		|| (status == TaskStatus.ALL);	
		}        
        
		public boolean isInsideInterval(Date from, Date to){
		    return this.startTime == null || (
		    		(from == null || this.startTime.after(from) &&
		    		(to == null || this.startTime.before(to))));	
		}

        public String getDuration() {
            if (startTime == null || finishTime == null) {
                return "";
            }
            // AK ugly, but working

            long ms = finishTime.getTime() - startTime.getTime();
            long days = ms / (24*60*60*1000);
            ms -= days * (24*60*60*1000);
            long hours = ms / (60*60*1000);
            ms -= hours * (60*60*1000);
            long minutes = ms / (60*1000);
            ms -= minutes * (60*1000);
            long secs = ms / 1000;
            return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, secs);
        }

        public JsonNode toJson() {
            JsonNodeFactory factory = new JsonNodeFactory(false);
            ObjectNode obj = factory.objectNode();
            obj.put("name", name);
            obj.put("status", status);
            obj.put("user", user);
            obj.put("taskType", taskType.name());
            obj.put("actionType", actionType);
            obj.put("id", id);
            obj.put("externalId", externalId);
            obj.put("home", home);
            obj.put("appFolder", appFolder);
                  
            if(parent != null){
            	obj.put("parent", parent);
            }
            if(startTime != null){
            	obj.put("startTime", dateFormat.format(startTime));
            }
            if(finishTime != null){
            	obj.put("finishTime", dateFormat.format(finishTime));
            }
            if(url != null){
            	obj.put("url", url);
            }
            
            obj.put("duration", getDuration());
            if(config !=  null){
            	obj.put("config", config);
            }

            if (startTime != null) {
                ObjectNode task = factory.objectNode();
                task.put("from", dateFormat.format(startTime));
                task.put("to", dateFormat.format(finishTime == null ? new Date(): finishTime));

                // FIXME chande to CSS classes
                if(Objects.equals(status, "SUCCEEDED")){ task.put("color", "#27E800");  }
                if(Objects.equals(status, "FINISHED")){ task.put("color", "#27E800");  }
                if(Objects.equals(status, "OK")){ task.put("color", "#27E800");  }
                if(Objects.equals(status, "RUNNING")){ task.put("color","#00E3AE"); }
                if(Objects.equals(status, "FAILED")){ task.put("color","#FF6600");}
                if(Objects.equals(status, "ERROR")){ task.put("color","#FF6600");}
                if(Objects.equals(status, "KILLED")){ task.put("color","#FF6600");}
                if(Objects.equals(status, "STOPED")){ task.put("color","#FF0000");}
                if(Objects.equals(status, "PAUSED")){ task.put("color","#A8A8A8");}
                if(Objects.equals(status, "UNKNOWN")){ task.put("color","#AB00FF");}

                ArrayNode tasks =  obj.putArray("tasks");
                tasks.add(task);
            }

            return obj;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Task task = (Task) o;
            return Objects.equals(id, task.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
        
        private static TaskList.Task findParent(Collection<TaskList.Task> list, TaskList.Task task){
        	if(task.parent == null){
        		return null;
        	}
        	for(TaskList.Task t:list)
        	{
        		if(t.id != null && t.id.equals(task.parent)){
        			return t;
        		}
        	};
        	return null;
        }
        
        private static List<TaskList.Task> findChild(Collection<TaskList.Task> list, TaskList.Task task){
        	List<TaskList.Task> result = new ArrayList<TaskList.Task>();
        	for(TaskList.Task t: list){
        		if(t.parent != null && t.parent.equals(task.id)){
        			result.add(t);
        			result.addAll(findChild(list, t));
        		}
        	}
        	
        	Collections.sort(result, new Comparator<TaskList.Task>(){
				@Override
				public int compare(Task t1, Task t2) {
	    	    	Date now = new Date();
	    	        Date time1 = t1.startTime == null ? now : t1.startTime;
	    	        Date time2 = t2.startTime == null ? now : t2.startTime;	    	    	
	    	    	return time1.compareTo(time2);				
	    	    }        		
        	});
        	return result;        			        			
        }
        
        public static List<TaskList.Task> buildSorted(Collection<TaskList.Task> list){
        	
        	List<TaskList.Task> resultList = new ArrayList<TaskList.Task>();
        	
        	List<TaskList.Task> sortedList = new ArrayList<TaskList.Task>(list);
        	
        	Collections.sort(sortedList, new Comparator<TaskList.Task>(){
				@Override
				public int compare(Task t1, Task t2) {
	    	    	Date now = new Date();
	    	        Date time1 = t1.startTime == null ? now : t1.startTime;
	    	        Date time2 = t2.startTime == null ? now : t2.startTime;	    	    	
	    	    	return time1.compareTo(time2);				
	    	    }        		
        	});        	
        	        	        
        	Iterator<TaskList.Task> iterator = sortedList.iterator();
        	while(iterator.hasNext()){
        		TaskList.Task t = iterator.next();
        		if(findParent(list, t) == null){
        			resultList.add(t);
        			resultList.addAll(findChild(list, t));
        		}
        	}
        	return resultList;
        }        
        
    }
    
    public static class ParentTask extends Task {
    	
    }

    public static class Workflow extends Task {
        public String workflowBody;        
    }
    
    public static class CoordinatorJob extends Task {
    	public String coordJobPath;
    	public Date nextMaterializedTime;
    	public String timeUnit;
    	public int timeOut;
    }  
    
    public static class CoordinatorAction extends Task {
    	public Date createdTime;
    	public String errorMessage;
    	public int actionNumber;
    }       

    public static class SparkTask extends Task {        
        public String externalStatus;
        public int numProcessed;
        public int numRejected;       

        @Override
        public JsonNode toJson() {
            JsonNodeFactory factory = new JsonNodeFactory(false);
            ObjectNode obj = factory.objectNode();
            obj.setAll((ObjectNode) super.toJson());
            obj.put("numProcessed", numProcessed);
            obj.put("numRejected", numRejected);
            obj.put("externalId", externalId);
            obj.put("externalStatus", externalStatus);

            ObjectNode runP = obj.putObject("runParams");
            for (Map.Entry<String, String> t: runParams.entrySet()) {
                runP.put(t.getKey(), t.getValue());
            }

            return obj;
        }

    }
    
    public interface TaskBuilder<T extends TaskList.Task> {
		T buildTask(JsonNode wf, TaskList.Task parent, Map<String, Object> defaultValues) throws ParseException; 
    }       
    
    public static class WorkflowTaskFactory<Workflow> implements TaskBuilder<TaskList.Workflow> { 
		@Override
		public TaskList.Workflow buildTask(JsonNode wf, TaskList.Task parent, Map defaultValues) throws ParseException {
    		SimpleDateFormat rfcDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            TaskList.Workflow task = new TaskList.Workflow();
            // TODO goto workflow details and parse Spark actions
            task.id = wf.get("id").textValue();
            task.externalId = wf.get("externalId").textValue();
            task.name = wf.get("appName").textValue();
            task.status = wf.get("status").textValue();
            task.user = wf.get("user").textValue();
            task.taskType = TaskList.TaskType.WORKFLOW; // TODO support coordinators/subs
            task.parent = null;//wf.get("parentId") == null ? null : wf.get("parentId").textValue());
            String startTime = wf.get("startTime").textValue();
            task.startTime = startTime == null ? null : rfcDateFormat.parse(startTime);
            String endTime = wf.get("endTime").textValue();
            task.finishTime = endTime == null ? null : rfcDateFormat.parse(endTime);
            task.url = wf.get("consoleUrl")  == null ? null : wf.get("consoleUrl").textValue();
            task.config = wf.get("conf") == null ? null : wf.get("conf").textValue();
            
            return task;
		}		
    }           
    
    public static class SparkTaskFactory<SparkTask> implements TaskBuilder<TaskList.SparkTask> {

		@Override
		public ru.neoflex.meta.runtime.TaskList.SparkTask buildTask(JsonNode wf, Task parent,
				Map<String, Object> defaultValues) throws ParseException {
			SimpleDateFormat rfcDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            TaskList.SparkTask task = new TaskList.SparkTask();
            task.id = wf.get("id").textValue();
            task.name = wf.get("name").textValue();
            task.externalId = wf.get("externalId").textValue();
            task.appFolder = wf.get("externalId").textValue();
            task.home = (String) defaultValues.get("home");
            task.status = wf.get("status").textValue();
            task.externalStatus = wf.get("externalStatus").textValue();
            task.user = parent == null ? null : parent.user;
            task.taskType = SPARK;
            task.actionType = wf.get("type") == null ? "workflow" : wf.get("type").textValue();;
            if(parent != null){
            	task.parent = parent.id;
            }
            
            String startTime = wf.get("startTime").textValue();
            task.startTime = startTime == null ? null : rfcDateFormat.parse(startTime); 
            String endTime = wf.get("endTime").textValue();
            task.finishTime = endTime == null ? null : rfcDateFormat.parse(endTime);
            task.url = wf.get("consoleUrl") == null ? null : wf.get("consoleUrl").textValue();
            task.config = wf.get("conf") == null ? null : wf.get("conf").textValue();
            task.numProcessed = 0;
            task.numRejected = 0;

            return task;
		}
    	
    }    
    
    public static class CoordinatorActionFactory<CoordinatorAction> implements TaskBuilder<TaskList.CoordinatorAction> {
    	
		@Override
		public ru.neoflex.meta.runtime.TaskList.CoordinatorAction buildTask(JsonNode wf,
				TaskList.Task parent,
				Map<String, Object> defaultValues) throws ParseException {
    		SimpleDateFormat rfcDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            TaskList.CoordinatorAction task = new TaskList.CoordinatorAction();
            task.id = wf.get("id").textValue();

            task.name = wf.get("toString").textValue();
            task.status = wf.get("status").textValue();
            task.taskType = TaskList.TaskType.COORDINATOR_ACTION;
            task.parent = wf.get("coordJobId") == null ? null : wf.get("coordJobId").textValue();
            String createdTime = wf.get("createdTime") == null ? null : wf.get("createdTime").textValue();            
            task.createdTime = createdTime == null ? null : rfcDateFormat.parse(createdTime);
            String lastModifiedTime = wf.get("lastModifiedTime") == null ? null : wf.get("lastModifiedTime").textValue();
            task.startTime = createdTime == null ? null : rfcDateFormat.parse(createdTime);
            task.finishTime = lastModifiedTime == null ? null : rfcDateFormat.parse(lastModifiedTime);
            task.url = wf.get("consoleUrl")  == null ? null : wf.get("consoleUrl").textValue();
            task.config = wf.get("runConf") == null ? null : wf.get("runConf").textValue();
            
            task.errorMessage = wf.get("errorMessage").textValue();
            task.actionNumber = wf.get("actionNumber").asInt();            
                        
            return task;
		}
	}
    public static class CoordinatorJobFactory<CoordinatorJob> implements TaskBuilder<TaskList.CoordinatorJob> { 
		@Override
		public ru.neoflex.meta.runtime.TaskList.CoordinatorJob buildTask(JsonNode wf,
				TaskList.Task parent, Map<String, Object> defaultValues) throws ParseException {
    		SimpleDateFormat rfcDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            TaskList.CoordinatorJob task = new TaskList.CoordinatorJob();
            // TODO goto workflow details and parse Spark actions
            task.id = wf.get("coordJobId").textValue();
            task.externalId = wf.get("coordExternalId").textValue();
            task.name = wf.get("coordJobName").textValue();
            task.status = wf.get("status").textValue();
            task.user = wf.get("user").textValue();
            task.taskType = TaskList.TaskType.COORDINATOR;
            task.parent = wf.get("parentId") == null ? null : wf.get("parentId").textValue();
            String startTime = wf.get("startTime") == null ? null : wf.get("startTime").textValue();
            task.startTime = startTime == null ? null : rfcDateFormat.parse(startTime);
            String endTime = wf.get("endTime").textValue();
            task.finishTime = endTime == null ? null : rfcDateFormat.parse(endTime);
            task.url = wf.get("consoleUrl")  == null ? null : wf.get("consoleUrl").textValue();
            task.config = wf.get("conf") == null ? null : wf.get("conf").textValue();
            
            task.coordJobPath = wf.get("coordJobPath").textValue();
            String nextMaterializedTime = wf.get("nextMaterializedTime").textValue();
            task.nextMaterializedTime = nextMaterializedTime == null ? null : rfcDateFormat.parse(nextMaterializedTime);
            task.timeUnit = wf.get("timeUnit").textValue();
            task.timeOut = wf.get("timeOut").asInt();
            
            return task;
		}
    	
    }
    
}
