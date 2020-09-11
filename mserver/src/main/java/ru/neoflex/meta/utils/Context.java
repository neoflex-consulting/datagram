package ru.neoflex.meta.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.neoflex.meta.svc.ContextSvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.System.identityHashCode;

/**
 * Created by orlov on 08.04.2015.
 */
public class  Context {
    private final static Log logger = LogFactory.getLog(Context.class);

    public Context getParent() {
        return parent;
    }

    public static class User {
        private String name;
        private String password;
        public User(String name, String password) {
            this.name = name;
            this.password = password;
        }
        public String getName() {
            return this.name;
        }
        public String getPassword() {
            return this.password;
        }
    }
    static class Db {
        Session session;
        Transaction transaction;
    }
    private Map<String, Db> databases = new HashMap<>();
    private User user;

    private ContextSvc contextSvc;
    private Context parent;

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        if (this.user != null) {
            return this.user;
        }
        if (this.getParent() != null) {
            return this.getParent().getUser();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();        

        if (authentication != null) {
            return new User(authentication.getName(), (String) authentication.getCredentials());
        }
        return new User(null, null);
    }

    public static Context getCurrent() {
        return ContextSvc.getCurrent();
    }

    public Context(ContextSvc contextSvc, Context parent) {
        this.contextSvc = contextSvc;
        this.parent = parent;
    }

    public void rollbackResources() {
        for (String dbtype: databases.keySet()) {
            Db db = databases.get(dbtype);
            if (db.transaction != null) {
                logger.debug(dbtype + ": rollback " + identityHashCode(db.transaction));
                try{ db.transaction.rollback(); } catch (Throwable th) {
                    logger.warn(dbtype + ": rollback failed", th);
                }
                db.transaction = null;
            }
            if (db.session != null) {
                logger.debug(dbtype + ": close " + identityHashCode(db.session));
                try{ db.session.close(); } catch (Throwable th) {
                    logger.warn(dbtype + ": close session", th);
                }
                db.session = null;
            }
        }
    }

    public void commitResources() {
        for (String dbtype: databases.keySet()) {
            commitTransaction(dbtype);
            Db db = databases.get(dbtype);
            if (db.session != null) {
                logger.debug(dbtype + ": close " + identityHashCode(db.session));
                try{ db.session.close(); } catch (Throwable th) {
                    logger.warn(dbtype + ": close session", th);
                }
                db.session = null;
            }
        }
    }

    private Db getDb(String dbType) {
        Db db = databases.get(dbType);
        if (db == null) {
            db = new Db();
            databases.put(dbType, db);
        }
        return db;
    }

    public Session getSession(String dbType, boolean transactional) {
        Db db = getDb(dbType);
        if (db.session == null) {
            db.session = contextSvc.getDbAdapter().getSessionFactory(dbType).openSession();
            logger.debug(dbType + ": open " + identityHashCode(db.session));
        }
        if (transactional) {
            beginTransaction(dbType);
        }
        return db.session;
    }

    public Session getSession() {
        return getSession("teneo", false);
    }

    public Session getTxSession() {
        return getSession("teneo", true);
    }

    public void beginTransaction(String dbType) {
        Db db = getDb(dbType);
        if (db.transaction == null) {
            db.transaction = db.session.beginTransaction();
            logger.debug(dbType + ": begin " + identityHashCode(db.transaction) + " in " + identityHashCode(db.session));
        }
    }

    public void begin() {
        beginTransaction("teneo");
    }

    public void commitTransaction(String dbType) {
        Db db = getDb(dbType);
        if (db.transaction != null) {
            logger.debug(dbType + ": commit " + identityHashCode(db.transaction));
            db.transaction.commit();
            db.transaction = null;
        }
    }

    public void commit() {
        commitTransaction("teneo");
    }

    public void savepoint() {
        commit();
        begin();
    }

    public ContextSvc getContextSvc() {
        return contextSvc;
    }

    public void setContextSvc(ContextSvc contextSvc) {
        this.contextSvc = contextSvc;
    }
}
