package ru.neoflex.meta.runtime;

import static ru.neoflex.meta.runtime.TaskList.TaskType.SPARK;
import static ru.neoflex.meta.runtime.TaskList.TaskType.WORKFLOW;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.kerberos.client.KerberosRestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.neoflex.meta.runtime.TaskList.TaskStatus;
import ru.neoflex.meta.runtime.TaskList.TaskType;

@Component
public class TaskProvider {
    private static final Logger log = LoggerFactory.getLogger(TaskProvider.class);

    private String taskManagerApi;
    private String oozieUser;
    private boolean isKerberosEnabled = false;
    private String keyTabLocation;
    private String userPrincipal;
    
    private final HdfsProvider hdfsProvider;
    
    public String getTaskManagerApi() {
		return taskManagerApi;
	}

	public void setTaskManagerApi(String oozieApi) {
		if(this.taskManagerApi != oozieApi){
			tasks.clear();	
		}		
		this.taskManagerApi = oozieApi;
	}
	
    public HdfsProvider getHdfsProvider() {
		return hdfsProvider;
	}

	private final ConcurrentMap<String, TaskList.Task> tasks = new ConcurrentHashMap<>();

    public TaskProvider(HdfsProvider hdfsProvider) {
        this.hdfsProvider = hdfsProvider;
    }

    public Collection<TaskList.Task> getTaskForPeriod(Date from, Date to, TaskList.TaskType taskType, TaskList.TaskStatus taskStatus) throws ParseException {
        fetchAllData(from, to, taskType, taskStatus);
        //checkCurrently();

        return filterTaskToShow(TaskList.Task.buildSorted(tasks.values()), from, to, taskStatus);
    }

    public TaskList.Task getTask(String taskId) throws ParseException {
        TaskList.Task task = tasks.get(taskId);
        if (task == null) {
            // if we didn't find task in map, try to reload info
            fetchAllData(null, null, TaskType.WORKFLOW, TaskStatus.ALL);
            task = tasks.get(taskId);
        }

        return task;
    }

    public Map killTask(String taskID) {
        TaskList.Task task = tasks.get(taskID);
        if (task != null && task.taskType == WORKFLOW || task != null && task.taskType == TaskType.COORDINATOR) {
            RestTemplate restTemplate = this.getTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(taskManagerApi + "/job/" + taskID + "?action=kill&user.name=" + oozieUser, HttpMethod.PUT, null, Map.class);
            return response.getBody();
        }
        return null;
    }
    
    public Map rerunTask(String taskID) {
        TaskList.Task task = tasks.get(taskID);
        if (task != null && task.taskType == WORKFLOW) {
            RestTemplate restTemplate = this.getTemplate();
            String request = 
            		"<configuration>" +
            			"<property><name>user.name</name><value>" + oozieUser + "</value></property>" +
            			"<property><name>oozie.wf.rerun.skip.nodes</name><value>:start:</value></property>" +
            			"</configuration>";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setAcceptCharset(Arrays.asList(Charset.forName("UTF-8")));

            HttpEntity<String> entity = new HttpEntity<String>(request ,headers);            
            
            //restTemplate.put(taskManagerApi + "/job/" + taskID + "?action=rerun&user.name=" + oozieUser, entity);
            ResponseEntity<Map> response = restTemplate.exchange(taskManagerApi + "/job/" + taskID + "?action=rerun&user.name=" + oozieUser, HttpMethod.PUT, entity, Map.class);
            return response.getBody();
        }
        if (task != null && task.taskType == TaskType.COORDINATOR) {
            RestTemplate restTemplate = this.getTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setAcceptCharset(Arrays.asList(Charset.forName("UTF-8")));   
            HttpEntity<String> entity = new HttpEntity<String>(".",headers);
                        
            ResponseEntity<Map> response = restTemplate.exchange(taskManagerApi + "/job/" + taskID + "?action=coord-rerun&type=action&scope=1-1&user.name=" + oozieUser,  HttpMethod.PUT, entity, Map.class);
            return response.getBody();
        }
        return null;
        
    }    

    List<TaskList.Task> filterTaskToShow(List<TaskList.Task> sortedTasks, Date from, Date to, TaskList.TaskStatus taskStatus) {
    	List<TaskList.Task> result = new ArrayList<TaskList.Task>(sortedTasks);
    	Iterator<TaskList.Task> iterator = result.iterator();
    	while (iterator.hasNext()) {
    		TaskList.Task task = iterator.next();
    		
    		if(!task.isInsideInterval(from, to) || (task.startTime == null && !task.status.equals("KILLED"))){
    			iterator.remove();
    		}
			
		}

        if (result.size() == 0 && sortedTasks.size() > 0) {
            result.add(sortedTasks.get(0));
        }
        
        iterator = result.iterator();
        //Фильтруем:
        //оставить:       
        //0. тех у кого есть ID
        //1. тех у кого нет родителя
        //2. тех у кого тип ВОРКФЛОУ
        //3. тех чьи родители в списке
        
    	while (iterator.hasNext()) {
    		TaskList.Task task = iterator.next();
    		if(task.taskType == WORKFLOW){
    			continue;
    		}
    		if(task.id != null && task.parent == null){
    			continue;
    		}
    		boolean parentFound = false;
    		if(task.parent != null){    			
				for(TaskList.Task tp: result){
					if(tp.id.equals(task.parent)){
						parentFound = true;
						break;
					}
				}			
    		}
    		
    		if(parentFound == false) {	
    			iterator.remove();
    		}
    	}

        return result;
    }
//WAITING, READY, SUBMITTED, RUNNING, SUSPENDED, TIMEDOUT, SUCCEEDED, KILLED, FAILED
    
    String getStatusFilter(TaskList.TaskStatus taskStatus){
    	if(taskStatus == TaskList.TaskStatus.ACTIVE){
    		return "&status=WAITING&status=READY&status=SUBMITTED&status=RUNNING&status=NEXT";
    	}
    	return "";
    }    
    
    void fetchAllData(Date from, Date to, TaskList.TaskType taskType, TaskList.TaskStatus taskStatus) throws ParseException {
    	if(taskType.equals(TaskList.TaskType.WORKFLOW)){
    		processTasks(taskManagerApi + "/jobs?len=50000" + getStatusFilter(taskStatus), "workflows", new TaskList.WorkflowTaskFactory(), null, from, to, taskStatus);	
    	}
    	if(taskType.equals(TaskList.TaskType.COORDINATOR)){
    		processTasks(taskManagerApi + "/jobs?jobtype=coord" + getStatusFilter(taskStatus), "coordinatorjobs", new TaskList.CoordinatorJobFactory(), null, from, to, taskStatus);
        }
    }
/*    
    void processSparkTasks(String restQuery, String collectionAttr, BiFunction<JsonNode, Optional<TaskList.Task>, Optional<TaskList.Task>> taskF,
            Optional<TaskList.Task> parentTask) {
		RestTemplate restTemplate = new RestTemplate();

		String rawData = restTemplate.getForObject(restQuery, String.class);
		try {
			ObjectMapper om = new ObjectMapper();
			JsonNode data = om.readTree(rawData.getBytes());
			Iterator<JsonNode> wfs = getJobsElements(data, collectionAttr);
			
			while (wfs.hasNext()) {
				JsonNode wf = wfs.next();
				String id = wf.get(getJobIdTag(wf)).textValue();
				// if we already parse finished workflow, skip it
				if (tasks.containsKey(id) && tasks.get(id).finishTime.isPresent()) {
				  continue;
				}
				Optional<TaskList.Task> task = taskF.apply(wf, parentTask);
				if (task.isPresent()) {
				  tasks.put(id, task.get());
				}
			}
			// need to update workflow config by details info
			if(parentTask.isPresent()){
				parentTask.get().config = of(data.get("conf").textValue());
				tasks.put(parentTask.get().id, parentTask.get());
			}
		} catch (IOException e) {
			log.error("Cannot retrieve info for url {}, error {}", taskManagerApi, e);
		}
	}
  */  
    private String getJobIdTag(JsonNode wf){	
    	return wf.get("id") == null ? "coordJobId" : "id";	
    }
    
    private Iterator<JsonNode> getJobsElements(JsonNode data, String collectionAttr){
    	return data.get(collectionAttr).elements();	   
    }
    
    private String getJodApiUrl(String id){
    	return taskManagerApi + "/job/" + id;	
    }
           
    public String getTaskAppId(TaskList.Task task) throws IOException, URISyntaxException{
    	if(task != null){
	    	if(task.parent != null){
	    		updateExternalIds(task.parent);	
	    	}
	    	return task.externalId;
    	} else {
    		return null;
    	}
    	
    }
    
    private RestTemplate getTemplate(){
    	if(this.isKerberosEnabled == true) {
    		return new KerberosRestTemplate(keyTabLocation, userPrincipal);
    	}
    	return new RestTemplate();
    }

    void processTasks(String restQuery, String collectionAttr, TaskList.TaskBuilder taskF,
                              TaskList.Task parentTask, Date from, Date to, TaskStatus status) throws ParseException {
    	RestTemplate restTemplate = getTemplate();
    	//RestTemplate
        // TODO parse not only last 50 items
        String rawData = restTemplate.getForObject(restQuery, String.class);
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode data = om.readTree(rawData.getBytes());
			Iterator<JsonNode> wfs = getJobsElements(data, collectionAttr);
            while (wfs.hasNext()) {
                JsonNode wf = wfs.next();
                String id = wf.get(getJobIdTag(wf)).textValue();
                // if we already parse finished workflow, skip it
                Map<String, Object> defaultValues = new HashMap<String, Object>();
                defaultValues.put("user", hdfsProvider.getHdfsDefaultUser());
                defaultValues.put("home", hdfsProvider.getBaseJobsPath());
                if (tasks.containsKey(id) && tasks.get(id) != null && tasks.get(id).finishTime != null) {
                    continue;
                }
                TaskList.Task task = taskF.buildTask(wf, parentTask, defaultValues);
                if (task != null && task.isInsideInterval(from, to) && task.isStatus(status)) {
                    tasks.put(id, task);
                    if (task.taskType == WORKFLOW) {
                        // fetch spark actions
                        processTasks(getJodApiUrl(id), "actions", new TaskList.SparkTaskFactory(), task, null, null, status);
                    }
                    if (task.taskType == TaskType.COORDINATOR) {
                        // fetch spark actions
                        processTasks(getJodApiUrl(id) + "?len=5000", "actions", new TaskList.CoordinatorActionFactory(), task, null, null, status);
                    }                    
                }
            }
            // need to update workflow config by details info
            if(parentTask != null){
                if (parentTask.status.equals("RUNNING") && parentTask.taskType.equals(TaskList.TaskType.WORKFLOW)) {
                    parseXML(parentTask, restTemplate);
                }
                parentTask.config = data.get("conf").textValue();
                tasks.put(parentTask.id, parentTask);            	
            }
        } catch (IOException e) {
            log.error("Cannot retrieve info for url {}, error {}", taskManagerApi, e);
        }
    }

    void checkCurrently(){
        Map.Entry<String, TaskList.Task> map;
        Iterator<Map.Entry<String, TaskList.Task>> it = tasks.entrySet().iterator();
        while(it.hasNext()) {
            map = it.next();
            TaskList.Task task = map.getValue();
            if(task.taskType.equals(TaskList.TaskType.SPARK) && tasks.containsKey(task.parent)){
                if(!tasks.get(task.parent).status.equals("RUNNING") && task.status.equals("NEXT")) {
                    it.remove();
                }
            }
        }
    }
        
    public static String escapeHtml(String string) {
        String escapedTxt = "";
        char tmp = ' ';
        for(int i = 0; i < string.length(); i++) {
            tmp = string.charAt(i);
            switch (tmp) {
                case '<':
                    escapedTxt += "&lt;";
                    break;
                case '>':
                    escapedTxt += "&gt;";
                    break;
                case '&':
                    escapedTxt += "&amp;";
                    break;
                case '"':
                    escapedTxt += "&quot;";
                    break;
                case '\'':
                    escapedTxt += "&#x27;";
                    break;
                case '/':
                    escapedTxt += "&#x2F;";
                    break;
                default:
                    escapedTxt += tmp;
            }
        }
        return escapedTxt;
    }    
    
    public String getOozieLog(String taskid){    	
		RestTemplate restTemplate = getTemplate(); 
		return restTemplate.getForObject(taskManagerApi + "/job/" + taskid + "?show=log", String.class);    	
    }
    
    private Document convertStringToDocument(String xmlStr) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try
        {
            builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlStr)));
        } catch (Exception e) {
            log.error("Cannot parse document {}, reason {}", xmlStr, e.getMessage());
            return null;
        }
    }    
    
    private void parseXML(TaskList.Task task, RestTemplate restTemplate){
        String file = restTemplate.getForObject(taskManagerApi + "/job/" + task.id + "?show=definition", String.class);
        Document doc = convertStringToDocument(file);
        if (doc == null) {
            return;
        }

        Element root = doc.getDocumentElement();
        NodeList action = root.getElementsByTagName("action");
        for (int i = 0; i < action.getLength(); i++) {
            String nameNode = action.item(i).getAttributes().item(0).getNodeValue();
            String idNode = task.id + "@" + nameNode;
            if (action.item(i).getChildNodes().item(1).getNodeName().equals("spark") && !tasks.containsKey(idNode)) {

                TaskList.SparkTask obj = new TaskList.SparkTask();
                obj.id = idNode;
                obj.name = nameNode;
                obj.taskType = TaskList.TaskType.SPARK;
                obj.status = "NEXT";
                obj.parent = task.id;
                obj.startTime = null;
                obj.finishTime = null;
                obj.url = null;
                obj.getDuration();
                obj.config = null;

                tasks.putIfAbsent(idNode, obj);
            }
        }
    }

    public void updateExternalIds(String taskid) throws IOException, URISyntaxException {
        // read workflow - spark job associations for all users
    	String hdfsUser = this.hdfsProvider == null ? null : this.hdfsProvider.getHdfsDefaultUser();
        List<String> assocs = hdfsProvider.readFromHdfs(hdfsUser, taskid, "", ".child");
        if(assocs.size() == 0){        	
        	assocs = hdfsProvider.readFromHdfs(hdfsUser, null, taskid + ".child", null);
        }
        Map<String, List<JsonNode>> am = new HashMap<>();
        ObjectMapper om = new ObjectMapper();
        for(String assoc: assocs){
            try {
                JsonNode data = om.readTree(assoc.getBytes());
                String id = data.get("child workflow").textValue();
                List<JsonNode> childs = am.get(id);
                if (childs == null) {
                    childs = new ArrayList<>();
                }
                childs.add(data);
                am.put(id, childs);
            } catch (IOException e) {
                log.error("Cannot parse association {}, error {}", assoc, e);
            }
        }
        // set correct externalId to spark application id
        // TODO set url for Spark History server
        // Weak comparation based on Oozie task name == part of Spark job name == className
        for(TaskList.Task t : tasks.values()){
        	if(t.parent != null && t.taskType == SPARK){
                List<JsonNode> assoc = am.get(t.parent);
                if (assoc != null) {
                	for(JsonNode data : assoc) {
                        JsonNode runParams = data.get("run params");                    
                        String appName = runParams.get("ACTION_NAME") != null ? runParams.get("ACTION_NAME").textValue() : data.get("app name").textValue();
                        if (appName.contains(t.name)) {
                            TaskList.SparkTask sparkTask = (TaskList.SparkTask) t;

                            sparkTask.externalId = data.get("appId").textValue();
                            sparkTask.appFolder = data.get("appId").textValue();
                            if (runParams != null) {
                                List<String> attributesToCopy = Arrays.asList(
                                        "IB_IMPORT_SESSION_ID",
                                        "RB_IMPORT_SESSION_ID",
                                        "PERIOD_DATE_FROM",
                                        "PERIOD_DATE_TO");
                                        //attributesToCopy
                                //for (String ac : runParams.fieldNames()) {
                                Iterator<String> fieldNames = runParams.fieldNames();
                                while(fieldNames.hasNext()){
                                	String ac = fieldNames.next();
                            		sparkTask.runParams.put(ac, runParams.get(ac).textValue());                                	
                                }
                            }
                        }
                    }
                }        		
        	}
        }
    }

	public String getOozieUser() {
		return oozieUser;
	}

	public void setOozieUser(String oozieUser) {
		if(this.oozieUser != oozieUser){
			tasks.clear();	
		}		
		
		this.oozieUser = oozieUser;
	}

	public boolean isKerberosEnabled() {
		return isKerberosEnabled;
	}

	public void setKerberosEnabled(boolean isKerberosEnabled) {
		this.isKerberosEnabled = isKerberosEnabled;
	}

	public String getKeyTabLocation() {
		return keyTabLocation;
	}

	public void setKeyTabLocation(String keyTabLocation) {
		this.keyTabLocation = keyTabLocation;
	}

	public String getUserPrincipal() {
		return userPrincipal;
	}

	public void setUserPrincipal(String userPrincipal) {
		this.userPrincipal = userPrincipal;
	}

}
