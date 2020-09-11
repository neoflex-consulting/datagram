package ru.neoflex.mspace.livy.job;

import java.util.List;

public class JobServerState {

    private List<String> uploadedJars;
    private int activeCount = 0;
    private int all = 0;
    private int idle = 0;
    private int created;


    public List<String> getUploadedJars() {
        return uploadedJars;
    }

    public void setUploadedJars(List<String> uploadedJars) {
        this.uploadedJars = uploadedJars;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    public int getAll() {
        return all;
    }

    public void setAll(int all) {
        this.all = all;
    }

    public int getIdle() {
        return idle;
    }

    public void setIdle(int idle) {
        this.idle = idle;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getCreated() {
        return created;
    }
}
