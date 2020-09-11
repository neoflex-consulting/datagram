package ru.neoflex.meta.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.ClientProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.json.internal.LazyMap;
import groovyx.net.http.HttpResponseDecorator;
import groovyx.net.http.RESTClient;

public class HDFSClient {
	private static final Logger log = LoggerFactory.getLogger(HDFSClient.class);

    String url;
    String user;
    Object config;
    RESTClient client;

    public HDFSClient(String url, String user, boolean isKerberosEnabled, String keyTabLocation, String userPrincipal) throws URISyntaxException {
        this.url = url;
        this.user = user;
        this.client = REST.getSimpleHTTPClient(url, isKerberosEnabled, keyTabLocation, userPrincipal);
    }
    
    private List<String> readFile(String path) throws ClientProtocolException, IOException, URISyntaxException {
    	log.info("WEB HDFS read file for url {}, ", getPath(path));
        Map<String, Object> opt = new HashMap<String, Object>();
        opt.put("path", getPath(path));
        opt.put("requestContentType", groovyx.net.http.ContentType.ANY);
        opt.put("contentType", groovyx.net.http.ContentType.BINARY);
        Map<String, Object> q = new HashMap<String, Object>();
        q.put("user.name", user);
        q.put("op", "OPEN");
        opt.put("query", q);
        HttpResponseDecorator response = (HttpResponseDecorator) client.get(opt);
        InputStream resp = (InputStream) response.getData();
        if(resp != null){
        	return Arrays.asList(IOUtils.toString(resp).split("\\n"));	
        } else {
        	return new ArrayList<String>();
        }
        
    }
    
    public String getFileStatus(String path) throws ClientProtocolException, IOException, URISyntaxException {    	    	
    	Map<String, Object> opt = new HashMap<String, Object>();
    	opt.put("path", getPath(path));
    	opt.put("requestContentType", groovyx.net.http.ContentType.ANY);
    	opt.put("contentType", groovyx.net.http.ContentType.JSON);
    	Map<String, Object> q = new HashMap<String, Object>();
    	q.put("user.name", user);
    	q.put("op", "GETFILESTATUS");
    	opt.put("query", q);
    	log.info("WEB HDFS read file status for url {}, ", path);
    	String fileStatus = "NOTFOUND";
    	try {
    		HttpResponseDecorator resp = (HttpResponseDecorator) this.client.get(opt);
    		Object res = resp.getData();
            groovy.json.internal.LazyMap map = (LazyMap) ((LazyMap) res).get("FileStatus");
            fileStatus = (String) map.get("type");

    	} catch (Exception e){
    		log.info("WEB HDFS error (NOT FOUND) read file status for url {}, ", path);
    	}
        
        return fileStatus;
    }    
    
    public List<String> listFiles(String path, String fileMask, String status) throws ClientProtocolException, IOException, URISyntaxException {    	    	
        String fileStatus = status == null ? getFileStatus(path) : status;
        if (fileStatus.equals("DIRECTORY")) {
        	log.info("WEB HDFS read dir for url {}, ", path);
        	List<String> result = new ArrayList<String>();
        	Map<String, Object> opt2 = new HashMap<String, Object>();
        	opt2.put("path", getPath(path));
        	opt2.put("requestContentType", groovyx.net.http.ContentType.ANY);
        	opt2.put("contentType", groovyx.net.http.ContentType.JSON);
        	Map<String, Object> q2 = new HashMap<String, Object>();
        	q2.put("user.name", user);
        	q2.put("op", "LISTSTATUS");
        	opt2.put("query", q2);        	
        	HttpResponseDecorator listFiles = (HttpResponseDecorator) this.client.get(opt2);
        	groovy.json.internal.LazyMap files = (LazyMap) listFiles.getData();
        	List<LazyMap> fs = (List<LazyMap>) ((LazyMap) files.get("FileStatuses")).get("FileStatus");
        	for(LazyMap f:fs) {
        		String fileName = (String) f.get("pathSuffix");
        		String type = (String) f.get("type");
        		if(type != null && type.equals("FILE")){	        		
	        		if(fileMask == null || (fileMask != null && fileName != null && fileName.contains(fileMask))) {
	        			result.addAll(this.readFile(path + "/" + fileName));
	        		}
        		}
        		if(type != null && type.equals("DIRECTORY")){
        			result.addAll(this.listFiles(path + "/" + fileName, fileMask, "DIRECTORY"));
        		}
        	}
        	return result;
        } 
        
        if(fileStatus.equals("FILE")){
        	return this.readFile(path);
        }
        
        return null;
    }    

    private String getPath(String path) {
        if (path.substring(0, 1).equals("/")) {
            return path.substring(1);
        } 
        else {
        	return path;
        }
    }

}
