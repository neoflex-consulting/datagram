package ru.neoflex.meta.runtime;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ru.neoflex.meta.utils.HDFSClient;

@Component
public class HdfsProvider {
	private static final Logger log = LoggerFactory.getLogger(HdfsProvider.class);
    private String baseJobsPath;
    private boolean isKerberos;
    private String keyTab;
    private String principal;
    private String webhdfs;    
    
    public String getBaseJobsPath() {
		return baseJobsPath;
	}

	private String hdfsDefaultUser;

    public String getHdfsDefaultUser() {
		return hdfsDefaultUser;
	}
    
    public HdfsProvider(){    	
    }
    
	public HdfsProvider(String defaultFS, String baseJobsPath, String hdfsDefaultUser, boolean isKerberos, String principal, String keyTab, String webhdfs) throws IOException {
    	reset(defaultFS, baseJobsPath, hdfsDefaultUser, isKerberos, principal, keyTab, webhdfs);
    }
    
    public void reset(String defaultFS, String baseJobsPath, String hdfsDefaultUser, boolean isKerberos, String principal, String keyTab, String webhdfs) throws IOException{
        this.baseJobsPath = baseJobsPath;
        this.hdfsDefaultUser = hdfsDefaultUser;
        this.keyTab = keyTab;
        this.principal = principal;  
        this.webhdfs = webhdfs;        
        this.isKerberos = isKerberos;
    }

    public List<String> readFromHdfs(final String user, final String appId, String fileName, String fileMask) throws IOException, URISyntaxException {
        final List<String> result = new ArrayList<>();
        HDFSClient hdfsClient = new HDFSClient(this.webhdfs + "/", user, this.isKerberos, keyTab, this.principal);
        
        String path = (user != null && appId != null) ?
                baseJobsPath + "/" + user + "/" + appId + "/" + fileName :
                baseJobsPath + "/" + hdfsDefaultUser + "/" + fileName;
        String status = hdfsClient.getFileStatus(path);
        if(!status.equals("NOTFOUND")){        	       
	        try {
	        	result.addAll(hdfsClient.listFiles(path, fileMask, status));	        		
	        } catch(Exception e){    
	        	log.error("Cannot retrieve info for url {}, error {}", path, e);
	        	return result;
	        }
        }
        
        return result;
    }
}
