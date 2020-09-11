package ru.neoflex.mspace.livy.job;

import org.apache.livy.Job;
import org.apache.livy.JobHandle;
import org.apache.livy.LivyClient;
import ru.neoflex.mspace.livy.common.OperationResult;
import ru.neoflex.mspace.livy.pool.LivyClientFactory;
import ru.neoflex.mspace.livy.pool.LivyClientPool;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class LivyJobHandler {

    private LivyClientPool LIVY_CLIENT_POOL = new LivyClientPool(new LivyClientFactory());


    private Map<String, List<LivyClient>> existingJars = new HashMap<String, List<LivyClient>>();
    private Map<JobHandle, LivyClient> activeSessions = new HashMap<JobHandle, LivyClient>();
    private final Map<String, JobHandle> jobHandles = new HashMap<String, JobHandle>();
    public final String EXTERNAL_ARGS = "externalArgs";

    public LivyJobHandler() {
    }


    public JobServerState getState(){

        JobServerState jobServerState = new JobServerState();
        jobServerState.setUploadedJars(new ArrayList<>(existingJars.keySet()));
        jobServerState.setActiveCount((int)LIVY_CLIENT_POOL.getBorrowedCount());
        jobServerState.setAll((int)LIVY_CLIENT_POOL.getMaxTotal());
        jobServerState.setIdle((int)LIVY_CLIENT_POOL.getNumIdle());
        jobServerState.setCreated((int)LIVY_CLIENT_POOL.getCreatedCount());
        return jobServerState;


    }

    public LivyJobHandler(String url, Properties properties) {
        LIVY_CLIENT_POOL = new LivyClientPool(new LivyClientFactory(url), properties);
    }


    public OperationResult submit(LivyJob job) {
        OperationResult operationResult = new OperationResult();
        try {
            JobHandle handle = getJobPair(job).getHandle();
            Object result = handle.get();
            operationResult.setState(JobHandle.State.SUCCEEDED);
            operationResult.setJobId(handle.toString());
            LIVY_CLIENT_POOL.returnObject(activeSessions.get(handle));
            return operationResult;
        } catch (Exception e) {
            e.printStackTrace();
            operationResult.setState(JobHandle.State.FAILED);
            operationResult.setErrors(Arrays.asList(e.toString()));
        }

        return operationResult;
    }

    public OperationResult submitNoWait(LivyJob job) {
        OperationResult operationResult = new OperationResult();
        try {
            JobPair jobPair = getJobPair(job);
            JobHandle handle = jobPair.getHandle();
            LivyClient client = jobPair.getClient();
            handle.addListener(new LivyJobListener(client));
            jobHandles.put(String.valueOf(handle.hashCode()), handle);
            operationResult.setState(JobHandle.State.QUEUED);
            operationResult.setJobId(handle.hashCode() + "");
            return operationResult;
        } catch (Exception e) {
            e.printStackTrace();
            operationResult.setState(JobHandle.State.FAILED);
            operationResult.setErrors(Arrays.asList(e.toString()));
        }

        return operationResult;
    }


    public OperationResult getStatus(String handle) {
        synchronized (jobHandles) {
            if (jobHandles.containsKey(handle)) {
                JobHandle jobHandle = jobHandles.get(handle);
                if (jobHandle.isCancelled() || jobHandle.isCancelled() || jobHandle.getState().equals(JobHandle.State.FAILED)) {
                    jobHandles.remove(handle);
                    LIVY_CLIENT_POOL.returnObject(activeSessions.get(handle));


                }
                return new OperationResult(jobHandle.getState(), handle, null);

            }
        }

        return new OperationResult(JobHandle.State.FAILED, null, null);


    }

    private JobPair getJobPair(LivyJob job) throws Exception {
        LivyClient clientForUse = null;
        String jarPath = job.getJarPath();
        boolean jarLoaded = false;
        File jobClassPathJar = new File(jarPath);
        if (!jobClassPathJar.exists()) {
            throw new NoSuchFieldException(jarPath);
        }
        clientForUse = LIVY_CLIENT_POOL.borrowObject();
        if (existingJars.containsKey(jarPath)) {
            jarLoaded = true;
        } else {

            clientForUse.uploadJar(jobClassPathJar);
            existingJars.put(jarPath, Arrays.asList(clientForUse));
        }
        if (!jarLoaded) {
            existingJars.put(jarPath, Arrays.asList(clientForUse));
        }
        String className = job.getClassName();
        URLClassLoader child = new URLClassLoader(
                new URL[]{jobClassPathJar.toURI().toURL()},
                this.getClass().getClassLoader()
        );
        child.loadClass(className);
        Class clazz = child.loadClass(className);
        Object instance_ = clazz.newInstance();
        Field params = instance_.getClass().getDeclaredField(EXTERNAL_ARGS);
        params.setAccessible(true);
        List<String> paramsList = job.getParams();
        params.set(instance_, paramsList.toArray(new String[paramsList.size()]));
        JobHandle handle = clientForUse.submit((Job) instance_);
        activeSessions.put(handle, clientForUse);
        return new JobPair(handle, clientForUse);
    }

    private class LivyJobListener implements JobHandle.Listener {
        private final LivyClient client;

        public LivyJobListener(LivyClient client) {
            this.client = client;
        }

        @Override
        public void onJobQueued(JobHandle jobHandle) {

        }

        @Override
        public void onJobStarted(JobHandle jobHandle) {

        }

        @Override
        public void onJobCancelled(JobHandle jobHandle) {
            LIVY_CLIENT_POOL.returnObject(client);
        }

        @Override
        public void onJobFailed(JobHandle jobHandle, Throwable throwable) {
            LIVY_CLIENT_POOL.returnObject(client);
        }

        @Override
        public void onJobSucceeded(JobHandle jobHandle, Object o) {
            LIVY_CLIENT_POOL.returnObject(client);
        }
    }


    class JobPair {
        JobHandle handle;
        LivyClient client;

        public JobHandle getHandle() {
            return handle;
        }

        public void setHandle(JobHandle handle) {
            this.handle = handle;
        }

        public LivyClient getClient() {
            return client;
        }

        public void setClient(LivyClient client) {
            this.client = client;
        }


        public JobPair(JobHandle handle, LivyClient client) {
            this.handle = handle;
            this.client = client;
        }
    }


}
