package ru.neoflex.mspace.livy.pool;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.livy.Job;
import org.apache.livy.JobContext;
import org.apache.livy.LivyClient;
import org.apache.livy.LivyClientBuilder;

import java.net.URI;
import java.util.concurrent.ExecutionException;

public class LivyClientFactory implements PooledObjectFactory<LivyClient> {

    private static final LivyConnectionStorage LIVY_CONNECTION_STORAGE = LivyConnectionStorage.getInstance();
    private String livyUrl = "";


    public LivyClientFactory() {
        livyUrl = LIVY_CONNECTION_STORAGE.getDefault();
    }

    public LivyClientFactory(String livyUrl) {
        this.livyUrl = livyUrl;
    }

    public PooledObject<LivyClient> makeObject() throws Exception {

        LivyClient client = new LivyClientBuilder()
                .setURI(new URI(livyUrl))
                .build();
        return new DefaultPooledObject<LivyClient>(client);
    }

    public void destroyObject(PooledObject<LivyClient> pooledObject) throws Exception {
        pooledObject.getObject().stop(true);
        pooledObject = null;
    }

    public boolean validateObject(PooledObject<LivyClient> pooledObject) {
        if(1==1) return true;
        LivyClient livyClient = pooledObject.getObject();

        try {
            return livyClient.submit(new SimpleCallableJob()).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void activateObject(PooledObject<LivyClient> pooledObject) throws Exception {
        if(validateObject(pooledObject)){
            //do nothing;
        }
    }

    public void passivateObject(PooledObject<LivyClient> pooledObject) throws Exception {

    }


    class SimpleCallableJob implements Job<Boolean>{
        @Override
        public Boolean call(JobContext jobContext) throws Exception {
            return true;
        }
    }
}
