package ru.neoflex.mspace.livy.jobserverrunner.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.mspace.livy.common.OperationResult;
import ru.neoflex.mspace.livy.job.LivyJob;
import ru.neoflex.mspace.livy.job.LivyJobHandler;

@RestController
public class LivyJobController {

    private static final LivyJobHandler jobHandler = new LivyJobHandler();


    @PostMapping("/submit")
    public OperationResult submit(@RequestBody LivyJob job){

        return jobHandler.submit(job);
    }


    @PostMapping("/submitNoWait")
    public OperationResult submitNoWait(@RequestBody LivyJob job){
        return jobHandler.submitNoWait(job);
    }

}
