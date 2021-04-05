package init

import org.hibernate.engine.spi.SessionFactoryImplementor
import ru.neoflex.meta.svc.PersistentEventsListener
import ru.neoflex.meta.utils.Context
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.hibernate.persister.entity.EntityPersister
import ru.neoflex.meta.utils.JSONHelper
import ru.neoflex.meta.utils.SymmetricCipher
import org.apache.commons.lang.StringUtils

import java.sql.Timestamp

Context.current.with {

    contextSvc.with {
        PersistentEventsListener.OnEvent setLastUpdatedMeta = new PersistentEventsListener.OnEvent() {
            @Override
            boolean execute(final String dbType, String eventName, final String entityName, final Map entity) {

                def result = false
                if(entity.keySet().contains("auditInfo")){                  
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication()
                    if (authentication == null) {
                        return false
                    }
                    String username = authentication.getName()
                    def now = new Timestamp(new Date().time)
                    Map auditInfo = entity.get("auditInfo")
                    if (auditInfo == null) {
                        EntityPersister ep = ((SessionFactoryImplementor)Context.getCurrent().getContextSvc().getTeneoSvc().getHbds().getSessionFactory()).getEntityPersister("auth.AuditInfo")
                        auditInfo = (Map) ep.getEntityTuplizer().instantiate()
                        auditInfo.put("createDateTime", now)
                        auditInfo.put("createUser", username)
                        Context.getCurrent().getSession("teneo", true).persist(auditInfo)
                        entity.put("auditInfo", auditInfo)
                    }
                    else {
                        def session = Context.current.contextSvc.getDbAdapter().getSessionFactory(dbType).openSession()
                        try {
                            def old = session.get( "auth.AuditInfo", auditInfo.e_id)
                            if (old != null && old.changeDateTime != null && auditInfo.changeDateTime != null && auditInfo.changeDateTime.before(old.changeDateTime)) {
                                throw new RuntimeException("Object ${entityName}(${entity.e_id}/${entity?.name}) modified by ${auditInfo.changeUser} at ${auditInfo.changeDateTime} was changed at ${old.changeDateTime} by ${old.changeUser}")
                            }
                            auditInfo.put("changeDateTime", now)
                            auditInfo.put("changeUser", username)
                        }
                        finally {
                            session.close()
                        }
                    }
                    result = true
                }
                return result
            }
        }

        PersistentEventsListener.OnEvent setTransformationRuntime = new PersistentEventsListener.OnEvent() {
            @Override
            boolean execute(final String dbType, String eventName, final String entityName, final Map entity) {

                def result = false
                if(entityName.toLowerCase().contains("transformation")){
                    def version = Context.current.getContextSvc().getBuildInfo().get("version")
                    if(entity.containsKey("sparkVersion")){
                        def sparkVersion = entity.get("sparkVersion");
                        def setVersion = "";
                        def newVersion = JSONHelper.getEnumerator("teneo", "etl.Transformation", "sparkVersion", "SPARK3")
                        if(version.toLowerCase().contains("spark2")){
                            newVersion = JSONHelper.getEnumerator("teneo", "etl.Transformation", "sparkVersion", "SPARK2")
                        }
                        entity.put("sparkVersion", newVersion);
                        result = true
                    }
                }
                return result
            }
        }
        getPersistentEventsListener().register(PersistentEventsListener.FLUSH_DIRTY_INTERCEPTOR, "teneo", setLastUpdatedMeta)
        getPersistentEventsListener().register(PersistentEventsListener.SAVE_INTERCEPTOR, "teneo", setLastUpdatedMeta)
        getPersistentEventsListener().register(PersistentEventsListener.PRE_INSERT_EVENT, "teneo", setTransformationRuntime)

        PersistentEventsListener.OnEvent encryptPassword = new PersistentEventsListener.OnEvent() {
            @Override
            boolean execute(final String dbType, String eventName, final String entityName, final Map entity) {
                def result = false
                if ("etl.Project" == entityName) {
                    entity.svnPassword = SymmetricCipher.encrypt(entity.svnPassword as String)
                    result = true
                }
                else if ("rt.JdbcConnection" == entityName) {
                    entity.password = SymmetricCipher.encrypt(entity.password as String)
                    result = true
                }
                else if ("auth.UserInfo" == entityName) {
                    entity.password = SymmetricCipher.encrypt(entity.password as String)
                    result = true
                }
                else if ("rt.ScheduledTask" == entityName) {
                    entity.password = SymmetricCipher.encrypt(entity.runAsPassword as String)
                    result = true
                }
                return result
            }
        }
        getPersistentEventsListener().register(PersistentEventsListener.FLUSH_DIRTY_INTERCEPTOR, "teneo", encryptPassword)
        getPersistentEventsListener().register(PersistentEventsListener.SAVE_INTERCEPTOR, "teneo", encryptPassword)

        PersistentEventsListener.OnEvent decryptPassword = new PersistentEventsListener.OnEvent() {
            @Override
            boolean execute(final String dbType, String eventName, final String entityName, final Map entity) {
                def result = false
                if ("etl.Project" == entityName) {
                    entity.svnPassword = SymmetricCipher.decrypt(entity.svnPassword as String)
                    result = true
                }
                else if ("rt.JdbcConnection" == entityName) {
                    entity.password = SymmetricCipher.decrypt(entity.password as String)
                    result = true
                }
                else if ("auth.UserInfo" == entityName) {
                    entity.password = SymmetricCipher.decrypt(entity.password as String)
                    result = true
                }
                else if ("rt.ScheduledTask" == entityName) {
                    entity.password = SymmetricCipher.decrypt(entity.runAsPassword as String)
                    result = true
                }
                return result
            }
        }
        getPersistentEventsListener().register(PersistentEventsListener.LOAD_INTERCEPTOR, "teneo", decryptPassword)

        PersistentEventsListener.OnEvent setName = new PersistentEventsListener.OnEvent() {
            @Override
            boolean execute(final String dbType, String eventName, final String entityName, final Map entity) {
                def result = false
                if (StringUtils.isEmpty(entity.name) && ("sm.SMInstance" == entityName || "sm.SMTransitionInstance" == entityName)) { 
                    entity.name = entity.smInstance.name + " " + entity.transition.toState.name + " " + (new Date()).format("yyyy MM dd HH mm ss")
                    result = true
                }
                if ("sm.SMInstance" == entityName && entity.currentState == null) {
                    entity.currentState = entity.stateMachine.start
                }
                
                if ("sm.SMTransitionInstance" == entityName) {
                    def transition = entity.transition 
                    entity.transitionDateTime = new Timestamp(new Date().time)
                    entity.user = SecurityContextHolder.getContext().getAuthentication()?.getName()
                    
                    if(transition.action) {
                        try {
                            entity.actionResult = Eval.me("entity", entity, transition.action)
                            entity.succesfull = true
                        } catch(Exception ex) {
                            entity.succesfull = false
                            entity.actionError = ex.message
                        }
                    }
                     
                    result = true
                }
                
                return result
            }
        }
        getPersistentEventsListener().register(PersistentEventsListener.FLUSH_DIRTY_INTERCEPTOR, "teneo", setName)
        getPersistentEventsListener().register(PersistentEventsListener.SAVE_INTERCEPTOR, "teneo", setName)
    }
    
}

