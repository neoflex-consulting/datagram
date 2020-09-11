package ru.neoflex.meta.svc;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.EmptyInterceptor;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.stereotype.Service;

/**
 * Created by orlov on 02.05.2015.
 */
@Service
public class PersistentEventsListener extends BaseSvc {

    public static final String FLUSH_DIRTY_INTERCEPTOR = "OnFlushDirtyInterceptor";
    public static final String SAVE_INTERCEPTOR = "OnSaveInterceptor";
    public static final String LOAD_INTERCEPTOR = "OnLoadInterceptor";
    public static final String DELETE_INTERCEPTOR = "OnDeleteInterceptor";
    public static final String SAVE_OR_UPDATE_EVENT = SaveOrUpdateEvent.class.getSimpleName();
    public static final String PRE_INSERT_EVENT = PreInsertEvent.class.getSimpleName();
    public static final String POST_INSERT_EVENT = PostInsertEvent.class.getSimpleName();
    public static final String POST_COMMIT_INSERT_EVENT = "PostCommitInsertEvent";
    public static final String PRE_UPDATE_EVENT = PreUpdateEvent.class.getSimpleName();
    public static final String POST_UPDATE_EVENT = PostUpdateEvent.class.getSimpleName();
    public static final String POST_COMMIT_UPDATE_EVENT = "PostCommitUpdateEvent";
    public static final String PRE_DELETE_EVENT = PreDeleteEvent.class.getSimpleName();
    public static final String POST_DELETE_EVENT = PostDeleteEvent.class.getSimpleName();
    public static final String POST_COMMIT_DELETE_EVENT = "PostCommitDeleteEvent";
    public static final String PRE_LOAD_EVENT = PreLoadEvent.class.getSimpleName();
    public static final String POST_LOAD_EVENT = PostLoadEvent.class.getSimpleName();

    public interface OnEvent {
        boolean execute(String factoryName, String eventName, String entityName, Map entity);
    };

    private Map<String, List<OnEvent>> events = new HashMap<>();

    public void register(String eventName, String dbType, String entityName, OnEvent onEvent) {
        register(onEvent, eventName, dbType, entityName);
    }

    public void register(String eventName, String dbType, OnEvent onEvent) {
        register(onEvent, eventName, dbType);
    }

    public void register(String eventName, OnEvent onEvent) {
        register(onEvent, eventName);
    }

    private void register(OnEvent onEvent, String... keyParts) {
        String key = "";
        for (String keyPart: keyParts) {
            key = key + "." + keyPart;
        }
        List<OnEvent> eventList = events.get(key);
        if (eventList == null) {
            eventList = new ArrayList<>();
            events.put(key, eventList);
        }
        eventList.add(onEvent);
    }

    private boolean onEvent(String dbType, String eventName, String entityName, Map entity, String... keyParts) {
        String key = "";
        boolean result = false;
        for (String keyPart: keyParts) {
            key = key + "." + keyPart;
            List<OnEvent> eventList = events.get(key);
            if (eventList != null) {
                for (OnEvent onEventHandler: eventList) {
                    boolean result2 = onEventHandler.execute(dbType, eventName, entityName, entity);
                    result = result || result2;
                }
            }
        }
        return result;
    }

    public boolean onEvent(String dbType, String eventName, String entityName, Map entity) {
        return onEvent(dbType, eventName, entityName, entity, eventName, dbType, entityName);
    }

    public Interceptor createInterceptor(String dbType) {
        return new LocalInterceptor(dbType);
    }

    private class LocalEventListener implements
            SaveOrUpdateEventListener,
            PreInsertEventListener, PostInsertEventListener, PostCommitInsertEventListener,
            PreUpdateEventListener, PostUpdateEventListener, PostCommitUpdateEventListener,
            PreDeleteEventListener, PostDeleteEventListener, PostCommitDeleteEventListener,
            PreLoadEventListener, PostLoadEventListener {

        private String dbType;
        private LocalEventListener(String dbType) {
            this.dbType = dbType;
        }

        @Override
        public void onSaveOrUpdate(SaveOrUpdateEvent event) throws HibernateException {
            if (event.getEntity() instanceof Map) {
                onEvent(dbType, event.getClass().getSimpleName(), event.getEntityName(), (Map) event.getEntity());
            }
        }

        @Override
        public boolean onPreInsert(PreInsertEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
            return false;
        }

        @Override
        public void onPostInsert(PostInsertEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPostUpdate(PostUpdateEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPostDelete(PostDeleteEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
        }

        @Override
        public boolean requiresPostCommitHanding(EntityPersister persister) {
            return true;
        }

        @Override
        public boolean onPreUpdate(PreUpdateEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
            return false;
        }

        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
            return false;
        }

        @Override
        public void onPostLoad(PostLoadEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPreLoad(PreLoadEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, event.getClass().getSimpleName(), entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPostDeleteCommitFailed(PostDeleteEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, POST_COMMIT_DELETE_EVENT, entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPostInsertCommitFailed(PostInsertEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, POST_COMMIT_INSERT_EVENT, entityName, (Map) event.getEntity());
            }
        }

        @Override
        public void onPostUpdateCommitFailed(PostUpdateEvent event) {
            if (event.getEntity() instanceof Map) {
                String entityName = event.getPersister().getEntityMetamodel().getName();
                onEvent(dbType, POST_COMMIT_UPDATE_EVENT, entityName, (Map) event.getEntity());
            }
        }
    }

    public void registerEventListeners(SessionFactory sessionFactory, String dbType) {
        EventListenerRegistry registry = ((SessionFactoryImpl) sessionFactory).getServiceRegistry().
                getService(EventListenerRegistry.class);
        LocalEventListener listener = new LocalEventListener(dbType);
        registry.getEventListenerGroup(EventType.SAVE_UPDATE).appendListener(listener);
        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_COMMIT_INSERT).appendListener(listener);
        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_COMMIT_UPDATE).appendListener(listener);
        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_COMMIT_DELETE).appendListener(listener);
        registry.getEventListenerGroup(EventType.PRE_LOAD).appendListener(listener);
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(listener);
    }

    private class LocalInterceptor extends EmptyInterceptor {
        private String dbType;
        private LocalInterceptor(String dbType) {
            this.dbType = dbType;
        }

        @Override
        public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
            if (entity instanceof Map) {
                Map map = copyState(state, propertyNames);
                String entityName = (String) ((Map)entity).get("_type_");
                map.put("e_id", id);
                map.put("_type_", entityName);
                if (entityName != null) {
                    boolean result = onEvent(dbType, SAVE_INTERCEPTOR, entityName, map);
                    if (result) {
                        modifyState(state, propertyNames, map);
                    }
                    return result;
                }
            }
            return super.onSave(entity, id, state, propertyNames, types);
        }

        private Map copyState(Object[] state, String[] propertyNames) {
            Map entity = new HashMap();
            for (int i = 0; i < propertyNames.length; ++i) {
                entity.put(propertyNames[i], state[i]);
            }
            return entity;
        }

        private void modifyState(Object[] state, String[] propertyNames, Map map) {
            for (int i = 0; i < propertyNames.length; ++i) {
                if (map.containsKey(propertyNames[i])) {
                    Object value = map.get(propertyNames[i]);
                    if (value == null && state[i] != null ||
                            value != null && (state[i] == null || !value.equals(state[i]))) {
                        state[i] = value;
                    }
                }
            }
        }

        @Override
        public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
            if (entity instanceof Map) {
                Map map = copyState(currentState, propertyNames);
                String entityName = (String) ((Map)entity).get("_type_");
                map.put("e_id", id);
                map.put("_type_", entityName);
                if (entityName != null) {
                    boolean result = onEvent(dbType, FLUSH_DIRTY_INTERCEPTOR, entityName, map);
                    if (result) {
                        modifyState(currentState, propertyNames, map);
                    }
                    return result;
                }
            }
            return super.onFlushDirty(entity, id, currentState, previousState, propertyNames, types);
        }

        @Override
        public void onDelete(
                Object entity,
                Serializable id,
                Object[] state,
                String[] propertyNames,
                Type[] types) {
            if (entity instanceof Map) {
                Map map = copyState(state, propertyNames);
                String entityName = (String) ((Map)entity).get("_type_");
                map.put("e_id", id);
                map.put("_type_", entityName);
                if (entityName != null) {
                    boolean result = onEvent(dbType, DELETE_INTERCEPTOR, entityName, map);
                    if (result) {
                        modifyState(state, propertyNames, map);
                    }
                }
            }
            super.onDelete(entity, id, state, propertyNames, types);
        }

        @Override
        public boolean onLoad(
                Object entity,
                Serializable id,
                Object[] state,
                String[] propertyNames,
                Type[] types) {
            if (entity instanceof Map) {
                Map map = copyState(state, propertyNames);
                String entityName = (String) ((Map)entity).get("_type_");
                map.put("e_id", id);
                map.put("_type_", entityName);
                if (entityName != null) {
                    boolean result = onEvent(dbType, LOAD_INTERCEPTOR, entityName, map);
                    if (result) {
                        modifyState(state, propertyNames, map);
                    }
                    return result;
                }
            }
            return super.onLoad(entity, id, state, propertyNames, types);
        }
    }
}
