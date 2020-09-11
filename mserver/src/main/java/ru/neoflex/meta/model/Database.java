package ru.neoflex.meta.model;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.engine.query.spi.NamedParameterDescriptor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.QueryImpl;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import ru.neoflex.meta.utils.Context;
import ru.neoflex.meta.utils.JSONHelper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by orlov on 08.04.2015.
 */
public class Database {
    String dbtype;

    public Database(String dbtype) {
        this.dbtype = dbtype;
    }

    public Database() {
        this("teneo");
    }

    public static Database getNew() {
        return new Database();
    }

    public Session getSession() {
        return Context.getCurrent().getSession();
    }

    public Session getTxSession() {
        return Context.getCurrent().getTxSession();
    }

    public Serializable save(String type, Map entity) {
        return Context.getCurrent().getSession(dbtype, true).save(type, entity);
    }

    public Serializable save(Map entity) {
        return save((String)entity.get("_type_"), entity);
    }

    public Map create(String type, Map entity) {
        save(type, entity);
        return entity;
    }

    public Map create(Map entity) {
        save(entity);
        return entity;
    }

    public void commit() {
        Context.getCurrent().commitTransaction(dbtype);
    }

    public void update(String type, Map entity) {
        Context.getCurrent().getSession(dbtype, true).update(type, entity);
    }

    public void update(Map entity) {
        update((String)entity.get("_type_"), entity);
    }

    public void saveOrUpdate(String type, Map entity) {
        Context.getCurrent().getSession(dbtype, true).saveOrUpdate(type, entity);
    }

    public void saveOrUpdate(Map entity) {
        saveOrUpdate((String)entity.get("_type_"), entity);
    }

    public Map merge(String type, Map entity) {
        return (Map) Context.getCurrent().getSession(dbtype, true).merge(type, entity);
    }

    public Map merge(Map entity) {
        return merge((String)entity.get("_type_"), entity);
    }

    public void delete(String type, Map entity) {
        Context.getCurrent().getSession(dbtype, true).delete(type, entity);
    }

    public Map load(String type, Serializable id) {
        Session session = Context.getCurrent().getSession(dbtype, false);
        return (Map) session.load(type, id);
    }

    public boolean loadAndDelete(String type, Serializable id) {
        Map entity = load(type, id);
        if (entity != null) {
            delete(type,entity);
        }
        return entity != null;
    }

    public boolean getAndDelete(String type, Serializable id) {
        Map entity = get(type, id);
        if (entity != null) {
            delete(type, entity);
        }
        return entity != null;
    }

    public Map get(String type, Map entity) {
        return (Map) getSession().get(type, new Long(entity.get(getIdName(type)).toString()));
    }

    public Map get(Map entity) {
        return get((String)entity.get("_type_"), entity);
    }

    public String getIdName(String type) {
        ClassMetadata metadata = getSession().getSessionFactory().getClassMetadata(type);
        return metadata.getIdentifierPropertyName();
    }

    public Map get(String type, Serializable id) {
        return (Map) Context.getCurrent().getSession(dbtype, false).get(type, id);
    }

    public List<Map> list(String type) {
        return list(type, new HashMap<String, Object>());
    }

    public List<Map> list(String type, Map<String,Object> requestParams) {
        String sql = "SELECT entity FROM  " + type + " entity";
        Query query = makeQuery(sql, requestParams);
        return query.list();
    }

    public List<Map> select(String sql, Map<String,Object> requestParams) {
        Session session = Context.getCurrent().getSession(dbtype, false);
        Query query = session.createQuery(sql);
        if (requestParams != null) {
            for (Map.Entry<String, Object> entry: requestParams.entrySet()) {
                String key = entry.getKey();
                if (key.equals("__first")) {
                    query.setFirstResult(Integer.valueOf((String)entry.getValue()));
                }
                else if (key.equals("__limit")) {
                    query.setMaxResults(Integer.valueOf((String)entry.getValue()));
                }
                else if (!key.startsWith("__")) {
                    NamedParameterDescriptor descriptor = ((QueryImpl) query).getParameterMetadata().getNamedParameterDescriptor(key);
                    Type type =  descriptor.getExpectedType();
                    Object value = JSONHelper.convertValue(session, null, type, entry.getValue());
                    query.setParameter(key, value);
                }
            }
        }
        return query.list();
    }



    public int executeUpdate(String sql, Map<String,Object> requestParams) {
        Session session = Context.getCurrent().getSession(dbtype, true);
        Query query = session.createQuery(sql);
        for (Map.Entry<String, Object> entry: requestParams.entrySet()) {
            String key = entry.getKey();
            NamedParameterDescriptor descriptor = ((QueryImpl) query).getParameterMetadata().getNamedParameterDescriptor(key);
            if (descriptor != null) {
                Type type =  descriptor.getExpectedType();
                Object value = JSONHelper.convertValue(session, null, type, entry.getValue());
                query.setParameter(key, value);
            }
        }
        return query.executeUpdate();
    }

    public int deleteWhere(String type, Map<String,Object> requestParams) {
        return executeUpdate("DELETE " + type, requestParams);
    }

    private class RequestParameter {
        String name;
        String op;
        Object value;

        public RequestParameter(String name, String op, Object value) {
            this.name = name;
            this.op = op;
            this.value = value;
        }
    }

    private List<RequestParameter> getRequestParameters(Map<String, Object> requestParams) {
        List<RequestParameter> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry: requestParams.entrySet()) {
            String key = entry.getKey();
            String op = null;
            String name = null;
            if (!key.startsWith("__")) {
                op = "=";
                name = key;
            }
            else if (key.startsWith("__lt_")) {
                op = "=";
                name = key.substring(5);
            }
            else if (key.startsWith("__gt_")) {
                op = ">";
                name = key.substring(5);
            }
            else if (key.startsWith("__le_")) {
                op = "<=";
                name = key.substring(5);
            }
            else if (key.startsWith("__ge_")) {
                op = ">=";
                name = key.substring(5);
            }
            if (name != null && op != null) {
                result.add(new RequestParameter(name, op, requestParams.get(key)));
            }
        }
        return result;
    }

    public Query makeQuery(String sql, Map<String, Object> requestParams) {
        return makeQuery(sql, requestParams, null);
    }

    public Query makeQuery(String sql, Map<String, Object> requestParams, String alias) {
        List<RequestParameter> requestParameters = getRequestParameters(requestParams);
        int i = 0;
        for (Map.Entry<String, Object> entry: requestParams.entrySet()) {
            String key = entry.getKey();
            if ("__join".equals(key)) {
                sql += " join " + entry.getValue();
            }
        }
        for (RequestParameter requestParameter: requestParameters) {
            String name = requestParameter.name;
            if (alias != null) {
                name = alias + "." + name;
            }
            String op = requestParameter.op;
            if (i == 0) {
                sql += " WHERE " + name + op + ":p" + i;
            }
            else {
                sql += " AND " + name + op + ":p" + i;
            }
            ++i;
        }
        String orderBy = (String)requestParams.get("__orderby");
        if (orderBy != null && !"".equals(orderBy)) {
            if (alias != null) {
                orderBy = alias + "." + orderBy;
            }
            sql += " ORDER BY " + orderBy;
        }
        Session session = Context.getCurrent().getSession(dbtype, false);
        Query query = session.createQuery(sql);
        for (Map.Entry<String, Object> entry: requestParams.entrySet()) {
            String key = entry.getKey();
            if (key.equals("__first")) {
                query.setFirstResult(Integer.valueOf((String)entry.getValue()));
            }
            else if (key.equals("__limit")) {
                query.setMaxResults(Integer.valueOf((String)entry.getValue()));
            }
        }
        i = 0;
        for (RequestParameter requestParameter: requestParameters) {
            String pname = "p" + i;
            NamedParameterDescriptor descriptor = ((QueryImpl) query).getParameterMetadata().getNamedParameterDescriptor(pname);
            Type type =  descriptor.getExpectedType();
            Object value = JSONHelper.convertValue(session, null, type, requestParameter.value);
            query.setParameter(pname, value);
            ++i;
        }
        return query;
    }

    void close() {
        Context.getCurrent().commitResources();
    }

    public void flush() {
        Context.getCurrent().getSession(dbtype, true).flush();
    }

    public void clear() {
        Context.getCurrent().getSession(dbtype, true).clear();
    }

    public void refresh(Object entity) {
        Context.getCurrent().getSession(dbtype, true).refresh(entity);
    }

    public Map<String, Object> instantiate(String entity) {
        EntityPersister ep = ((SessionFactoryImplementor)Context.getCurrent().getContextSvc().getTeneoSvc().getHbds().getSessionFactory()).getEntityPersister(entity);
        return (Map) ep.getEntityTuplizer().instantiate();
    }

    public Map<String, Object> instantiate(String entity, Map<String, Object> props) {
        Map<String, Object> result = instantiate(entity);
        result.putAll(props);
        return result;
    }
}
