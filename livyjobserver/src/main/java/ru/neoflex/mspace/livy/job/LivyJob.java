package ru.neoflex.mspace.livy.job;

import java.util.List;

public class LivyJob {

    public LivyJob() {
    }

    public LivyJob(String jarPath, String className, List<String> params) {
        this.jarPath = jarPath;
        this.className = className;
        this.params = params;
    }

    private String jarPath;
    private String className;
    private List<String> params;

    public List<String> getParams() {
        return params;
    }

    public void setParams(List<String> params) {
        this.params = params;
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}
