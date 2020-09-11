package ru.neoflex.meta.controllers;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.svc.BaseSvc;
import ru.neoflex.meta.svc.ContextSvc;
import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.utils.JSONHelper;
import ru.neoflex.meta.utils.TraversalStrategy;
import ru.neoflex.mspace.livy.common.OperationResult;
import ru.neoflex.mspace.livy.job.JobServerState;
import ru.neoflex.mspace.livy.job.LivyJob;
import ru.neoflex.mspace.livy.job.LivyJobHandler;

import org.springframework.web.bind.annotation.PathVariable;

import javax.transaction.Transactional;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Controller
@RequestMapping("/livy")
public class LivyController {

    @Autowired
    private ContextSvc contextSvc;

    private static final Map<String, LivyJobHandler> JOB_HANDLER_LIST = new HashMap<String, LivyJobHandler>();


    @PostMapping("/submit/{livyServer}")
    public @ResponseBody
    OperationResult submit(@PathVariable("livyServer") String livyServer, @RequestBody LivyJob job) {
        LivyJobHandler livyJobHandler = getJobHandler(livyServer);
        return livyJobHandler.submit(job);
    }


    @GetMapping("/state/{livyServer}")
    public @ResponseBody
    JobServerState getState(@PathVariable("livyServer") String livyServer) {
        LivyJobHandler livyJobHandler = getJobHandler(livyServer);
        return livyJobHandler.getState();
    }

    private static Properties getPoolProperties() {
        try {
            File propsFile = new File(BaseSvc.getMSpaceDir(), "livy.properties");
            Properties properties = new Properties();
            properties.load(new FileReader(propsFile));
            return properties;
        } catch (Exception e) {
            return null;
        }
    }


    @Transactional
    private LivyJobHandler getJobHandler(String livyServer) {

        LivyJobHandler livyJobHandler = null;
        if (!JOB_HANDLER_LIST.containsKey(livyServer)) {
            try {
                final String[] url = new String[1];
                contextSvc.inContext(new Runnable() {
                    @Override
                    public void run() {
                        Database db = new Database();
                        //String sql = "select * from rt.LivyServer where name = '" + livyServer + "'";
                        List<Map> list = db.list("rt.LivyServer", Collections.singletonMap("name", livyServer));
                        Map first = list.get(0);
                        url[0] = (String)
                                first.get("http");
                    }
                });

                livyJobHandler = new LivyJobHandler(url[0], getPoolProperties());
                JOB_HANDLER_LIST.putIfAbsent(livyServer, livyJobHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            livyJobHandler = JOB_HANDLER_LIST.get(livyServer);
        }
        return livyJobHandler;
    }


    @PostMapping("/submitNoWait/{livyServer}")
    public @ResponseBody
    OperationResult submitNoWait(@PathVariable("livyServer") String livyServer, @RequestBody LivyJob job) {
        LivyJobHandler livyJobHandler = getJobHandler(livyServer);
        return livyJobHandler.submitNoWait(job);
    }

    @GetMapping("/job/{livyServer}/{handle}")
    public @ResponseBody
    OperationResult getStatus(@PathVariable("livyServer") String livyServer, @PathVariable("handle") String handle) {
        LivyJobHandler livyJobHandler = getJobHandler(livyServer);
        return livyJobHandler.getStatus(handle);
    }
}
