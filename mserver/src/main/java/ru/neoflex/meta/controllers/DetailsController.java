package ru.neoflex.meta.controllers;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.neoflex.meta.runtime.HdfsProvider;
import ru.neoflex.meta.runtime.TaskList;
import ru.neoflex.meta.runtime.TaskProvider;

@RestController
@RequestMapping("/admin2")
public class DetailsController {
	
	@Autowired
	private Environment properties;
	
    private static final Logger log = LoggerFactory.getLogger(DetailsController.class);

    private final HdfsProvider hdfsProvider;
    private final TaskProvider oozieProvider;

    public DetailsController(HdfsProvider hdfsProvider, TaskProvider oozieProvider) {
        this.hdfsProvider = hdfsProvider;
        this.oozieProvider = oozieProvider;
    }

    @RequestMapping("/job/{user}/{job_id}/logs")
    public String jobLogs(@PathVariable("user") String user,
                          @PathVariable("job_id") String jobId) throws ParseException, IOException, URISyntaxException {
    	ObjectMapper om = new ObjectMapper();
    	List<String> res = buildCollection(user, jobId, "logs.json", null, new Converter());
        return om.writeValueAsString(res);
    }
    
    @RequestMapping("/job/{user}/{job_id}/oozielog")
    public String jobOozieLog(@PathVariable("user") String user,
                          @PathVariable("job_id") String jobId) throws JsonProcessingException {
     	String taskId = jobId;
        String result = oozieProvider.getOozieLog(taskId);
		ObjectMapper om = new ObjectMapper();
		List<String> s = Arrays.asList(result);
		return om.writeValueAsString(s);
    }    
    
    private List<String> getRejects(String user, String jobId) throws ParseException, IOException, URISyntaxException {
    	List<String> res = buildCollection(user, jobId, "rejects.json", ".json", new Converter(){
            private String convertRejectString(String r) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNodeFactory factory = new JsonNodeFactory(false);

                try {
                    JsonNode rec = objectMapper.readTree(r);
                    Date stamp = new Date(rec.get("ts").longValue()); //LocalDateTime.ofInstant(Instant.ofEpochMilli(rec.get("ts").longValue()),
                            //TimeZone.getDefault().toZoneId());

                    ObjectNode result = factory.objectNode();
                    Iterator<Entry<String, JsonNode>> i = rec.fields();
                    SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    while(i.hasNext()){
                    	Entry<String, JsonNode> e = i.next();
                        String key = e.getKey();
                        JsonNode val = e.getValue();
                        result.put(key, val.textValue());
                        if (key.equals("ts")) {                	 
                            result.put("ts", s.format(stamp));
                        }            	
                    }

                    return result.toString();
                } catch (IOException e) {
                    log.error("Cannot parse reject record {}", r, e);
                }
                return "";
            }
            @Override
            String convert(String s){
            	return this.convertRejectString(s);
            }
        });
    	return res;
    }
    
    @RequestMapping("/job/{user}/{job_id}/rejects")
    public String jobRejects(@PathVariable("user") String user,
                             @PathVariable("job_id") String jobId) throws ParseException, IOException, URISyntaxException {
    	ObjectMapper om = new ObjectMapper();
    	return om.writeValueAsString(getRejects(user, jobId));
    }
    
    @RequestMapping("/job/{user}/{job_id}/runparams")
    public String jobRunParams(@PathVariable("user") String user,
                             @PathVariable("job_id") String jobId) throws JsonProcessingException, ParseException {
        TaskList.Task task = oozieProvider.getTask(jobId);

        ObjectMapper om = new ObjectMapper();
        List<String> res = new ArrayList<String>();
        if(task != null){
        	Iterator<Entry<String, JsonNode>> i = task.toJson().get("runParams").fields();
        	while(i.hasNext()){
        		Entry<String, JsonNode> a = i.next();
	        	if(!a.getKey().toUpperCase().contains("PASSWORD")){
	        		res.add(a.getKey() + "=" + a.getValue().textValue());
	        	}        		
        	}
        }
        Collections.sort(res, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1.toUpperCase().compareTo(o2.toUpperCase());
			}
		});

        return om.writeValueAsString(res);
    } 
    
    @RequestMapping("/job/{user}/{job_id}/appid")
    public String appid(@PathVariable("user") String user,
    		@PathVariable("job_id") String jobId) throws ParseException, IOException, URISyntaxException {    
	    TaskList.Task task = oozieProvider.getTask(jobId);	    
	    if(task != null){
	    	ObjectMapper om = new ObjectMapper();
	    	List<String> res = new ArrayList<String>();
	    	res.add(oozieProvider.getTaskAppId(task));
	    	return om.writeValueAsString(res);
	    }
	    return null;
    }
    
    @RequestMapping("/mserver-api")
    @ResponseBody
    public String getMetaServerApiUrl() throws IOException{
    	JsonNodeFactory factory = new JsonNodeFactory(false);    	
    	return factory.arrayNode().add(factory.textNode(properties.getProperty("mserver.api", "http://localhost:8080/api"))).toString();
    }

    @RequestMapping("/tasks/{from}/{to}")
    public synchronized String getTasks(@PathVariable("from") @DateTimeFormat(iso= DATE) Date from,
                            @PathVariable("to") @DateTimeFormat(iso= DATE) Date to,
                            @RequestParam(name="oozie", required=false) String oozie,
                            @RequestParam(name="serverType", required=false) String serverType,
                            @RequestParam(name="server", required=false) String server,
                            @RequestParam(name="taskType", required=false) String taskType,
                            @RequestParam(name="taskStatus", required=false) String taskStatus,
                            @RequestParam(name="nameNode", required=false) String nameNode,
                            @RequestParam(name="home", required=false) String home,
                            @RequestParam(name="user", required=false) String user) throws JsonParseException, JsonMappingException, IOException, ParseException, URISyntaxException {
    	TaskList.TaskType taskTypeE = TaskList.TaskType.valueOf(taskType.toUpperCase()); 
    	TaskList.TaskStatus taskStatusE = TaskList.TaskStatus.valueOf(taskStatus.toUpperCase());
		oozieProvider.setTaskManagerApi(oozie);
		String defaultFS = nameNode;
		String baseJobsPath = home;
		String hdfsDefaultUser = user;
		
		
		if(server != null && !server.equals("null")){				
			ObjectMapper mapper = new ObjectMapper();
	
			Map<String, Object> map = new HashMap<String, Object>();
			map = mapper.readValue(server, new TypeReference<Map<String, Object>>(){});
			
			defaultFS = (String) map.get("nameNode");
			baseJobsPath = (String) map.get("home");
			hdfsDefaultUser = (String) map.get("user");
			
			oozieProvider.getHdfsProvider().reset(defaultFS, baseJobsPath, hdfsDefaultUser, 
					(boolean) map.get("isKerberosEnabled"), 
					(String) map.get("userPrincipal"), 
					(String) map.get("keyTabLocation"),
					(String) map.get("webhdfs"));
			oozieProvider.setOozieUser((String) map.get("user"));
			oozieProvider.setKerberosEnabled((boolean) map.get("isKerberosEnabled"));
			oozieProvider.setUserPrincipal((String) map.get("userPrincipal"));
			oozieProvider.setKeyTabLocation((String) map.get("keyTabLocation"));
		} else {
			oozieProvider.getHdfsProvider().reset(defaultFS, baseJobsPath, hdfsDefaultUser, false, null, null, null);
			oozieProvider.setOozieUser(hdfsDefaultUser);			
		}
		Date toDate = to == null ? null :
			DateUtils.addDays(DateUtils.truncate(to, Calendar.DAY_OF_MONTH), 1);
			
        Collection<TaskList.Task> tasks = oozieProvider.getTaskForPeriod(from, toDate, taskTypeE, taskStatusE);
        JsonNodeFactory factory = new JsonNodeFactory(false);
        ArrayNode jTasks = factory.arrayNode();
        for (TaskList.Task t : tasks) {
            jTasks.add(t.toJson());
        }
        return jTasks.toString();
    }

    @RequestMapping(value = "/task/{task_id}/kill"  , method = RequestMethod.PUT)
    public Map killTask(@PathVariable("task_id") String taskId) {
        return oozieProvider.killTask(taskId);
    }
    
    @RequestMapping(value = "/task/{task_id}/rerun"  , method = RequestMethod.PUT)
    public Map rerunTask(@PathVariable("task_id") String taskId) {
        return oozieProvider.rerunTask(taskId);
    }
    
    class Converter {
    	String convert(String s){
    		return s;
    	}
    }
    
    private List<String> buildCollection(String user, String jobId, String fileName, String fileMask, Converter converter) throws ParseException, IOException, URISyntaxException {
        // remap task id to spark application id
        TaskList.Task task = oozieProvider.getTask(jobId);
        if(task != null){
        	String appId = oozieProvider.getTaskAppId(task);        	
        	
        	if(task.taskType == TaskList.TaskType.SPARK || task.taskType == TaskList.TaskType.JOBSERVER){
        		List<String> list = hdfsProvider.readFromHdfs(user, appId, fileName, fileMask);
        		List<String> result = new ArrayList<String>();
        		if(list != null){
        			Iterator<String> i = list.iterator();
        			while(i.hasNext()){
        				result.add(converter.convert(i.next()));        				
        			}
        		}
        		return result;
        	} else {
        		return Arrays.asList((new String[0]));
        	}
        	
        } else {
        	return Arrays.asList((new String[0]));
        }
    }

}
