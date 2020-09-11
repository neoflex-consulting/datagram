package ru.neoflex.mspace.livy.common;

import org.apache.livy.JobHandle;

import java.util.List;

public class OperationResult {


    public OperationResult() {
    }

    public OperationResult(JobHandle.State state, String jobId, List<String> errors) {
        this.state = state;
        this.jobId = jobId;
        this.errors = errors;
    }

    private JobHandle.State state;
    private String jobId;
    private List<String> errors;


    public JobHandle.State getState() {
        return state;
    }

    public void setState(JobHandle.State state) {
        this.state = state;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }


}
