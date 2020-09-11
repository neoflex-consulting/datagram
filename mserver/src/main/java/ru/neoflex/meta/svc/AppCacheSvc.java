package ru.neoflex.meta.svc;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@Service
public class AppCacheSvc {
    private static final ConcurrentMap<String, Object> cache = new ConcurrentHashMap<>();

    public Object get(String key) {
        return cache.get(key);
    }

    public Object computeIfAbsent(String key, Function<String, Object> computeFn) {
        return cache.computeIfAbsent(key, computeFn);
    }

    public Object put(String key, Object val) {
        return cache.put(key, val);
    }

    public Object putIfAbsent(String key, Object val) {
        return cache.putIfAbsent(key, val);
    }
}
