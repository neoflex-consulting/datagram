package ru.neoflex.mspace.livy.pool;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.livy.LivyClient;

import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;


public class LivyClientPool extends GenericObjectPool<LivyClient> {


    public LivyClientPool(PooledObjectFactory<LivyClient> factory) {
        super(factory);
        GenericObjectPoolConfig<LivyClient> config = new GenericObjectPoolConfig<LivyClient>();
        config.setMinIdle(5);
        config.setMaxIdle(20);
        setConfig(config);
    }

    public LivyClientPool(PooledObjectFactory<LivyClient> factory, GenericObjectPoolConfig<LivyClient> config) {
        super(factory, config);
    }

    public LivyClientPool(PooledObjectFactory<LivyClient> factory, GenericObjectPoolConfig<LivyClient> config, AbandonedConfig abandonedConfig) {
        super(factory, config, abandonedConfig);
    }

    public LivyClientPool(LivyClientFactory factory, Properties properties) {
        super(factory);
        try {
            GenericObjectPoolConfig<LivyClient> config = new GenericObjectPoolConfig<LivyClient>();
            if (properties != null) {
                Enumeration names = properties.propertyNames();
                while (names.hasMoreElements()) {
                    Object key = names.nextElement();
                    Field field_ = config.getClass().getDeclaredField((String) key);
                    field_.setAccessible(true);
                    Object value = null;
                    if(field_.getType().equals(Integer.class)){
                        value = Integer.valueOf(properties.getProperty((String)key));
                    }
                    if(field_.getType().equals(Long.class)){
                        value = Long.valueOf(properties.getProperty((String)key));
                    }
                    field_.set(config, value);
                }
            }
        }catch (Exception e){
            System.err.println("Error setting properties");
            e.printStackTrace();
        }





    }
}
