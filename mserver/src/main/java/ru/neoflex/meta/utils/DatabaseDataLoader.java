package ru.neoflex.meta.utils;

import fmpp.Engine;
import fmpp.tdd.DataLoader;
import ru.neoflex.meta.model.Database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by orlov on 16.05.2015.
 */
public class DatabaseDataLoader implements DataLoader {
    @Override
    public Object load(Engine e, List args) throws Exception {
        if (args.size() != 3 || !(args.get(2) instanceof Map)) {
            throw new IllegalArgumentException("DatabaseDataLoader.load syntax: dbtype type {queryParam1: paramValue1 ...}");
        }
        String dbtype = (String) args.get(0);
        Database db = new Database(dbtype);
        String type = (String) args.get(1);
        Map argsMap = (Map) args.get(2);
        List<Map> result = db.list(type, argsMap);
        return result;
    }
}
