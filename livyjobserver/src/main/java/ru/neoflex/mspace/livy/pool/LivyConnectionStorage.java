package ru.neoflex.mspace.livy.pool;

import java.util.HashMap;

public class LivyConnectionStorage extends HashMap<String, String> {

    private static LivyConnectionStorage INSTANCE;


    private LivyConnectionStorage(String default_){
        put(DEFAULT, default_);
    }

    public static LivyConnectionStorage getInstance(){
        return new LivyConnectionStorage(System.getProperty("LIVY_URL"));
    }




    private static final String DEFAULT = "DEFAULT";

    public String getOrDefault(String name){
        if(null == name || name.isEmpty() || !containsKey(name)){
            if(containsKey(DEFAULT)){
                return get(DEFAULT);
            }
            throw new RuntimeException("No livy connections available");
        }
        return get(name);
    }

    public String getDefault(){
        return getOrDefault(DEFAULT);
    }




}
