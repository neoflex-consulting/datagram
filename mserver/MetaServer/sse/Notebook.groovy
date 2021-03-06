package MetaServer.sse

import MetaServer.rt.LivyServer
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import ru.neoflex.meta.model.Database
import ru.neoflex.meta.svc.ContextSvc
import ru.neoflex.meta.svc.GitflowSvc
import ru.neoflex.meta.utils.Context
import ru.neoflex.meta.utils.JSONHelper

import java.time.LocalDateTime
import java.util.concurrent.*
import java.util.function.Function

class Notebook {
    private static final Log logger = LogFactory.getLog(Notebook.class)

    static Map addParagraph(Map entity, Map params = null) {
        def index = Integer.parseInt(params?.index ?: "-1")
        def db = Database.new
        def nb = db.get(entity)

        def p = db.instantiate("sse.Paragraph")
        p.name = nb.name + "_" + LocalDateTime.now().toString()
        p.body = db.instantiate("sse.CodeBody")
        p.textVisible = true
        p.resultVisible = true
        p.titleVisible = true

        if (index >= 0) {
            (nb.paragraphs as List).add(index, p)
        }
        else {
            nb.paragraphs.add(p)
        }
        db.saveOrUpdate(nb)
        db.commit()

        return [status: "OK", entity: JSONHelper.toJSON("teneo", "sse.Notebook", db.get(nb)), problems: []]
    }

    static Map runAll(Map entity, Map params = null) {
        def db = Database.new
        def nb = db.get(entity)

        nb.paragraphs.each {
            Map p -> Paragraph.run(p, (params ?: [:]) << [nb_id: nb.e_id])
        }

        db.saveOrUpdate(nb)

        return [status: "OK", problems: []]
    }

    static void submit(notebook, paragraph, params) {
        def livy = Workspace.getLivyServer((Map) notebook.workspace)

        if (livy == null) {
            logger.error("Cannot find livy server for notebook ${notebook.name}")
            paragraph.setErrorResult("Cannot find livy server for notebook ${notebook.name}", "", "")
            return
        }

        def nb_id = Integer.valueOf((String) notebook.e_id)
        def livySession = notebooks.computeIfAbsent(nb_id, new Function<Integer, LivySession>() {
            @Override
            LivySession apply(Integer k) {
                return new LivySession(livy)
            }
        })
        try {
            livySession.submitParagrpah(paragraph, params)
        } catch (Throwable e) {
            logger.error("Error while submit paragraph to execute", e)
            paragraph.setErrorResult("Error while submit paragraph to execute", "", e.getMessage())
        }
    }

    // Notebook sessions cache mgmt
    // TODO add logic for termination of executors
    // Clear all sessions
    static void clearAllSessions(Map entity, Map params = null) {
        getNotebooks().clear()
    }

    // Clear session for specified Notebook
    static void clearNotebookSession(Map entity, Map params = null) {
        def nb_id = Integer.valueOf((String) entity.e_id)
        getNotebooks().remove(nb_id)
    }

    static Object getSessionInfo(Map entity, Map params = null) {
        def nb_id = Integer.valueOf((String) entity.e_id)
        return getNotebooks().get(nb_id).getSessionInfo()
    }

    private static ConcurrentMap<Integer, LivySession> getNotebooks() {
        def appCache = Context.getCurrent().contextSvc.appCacheSvc
        return appCache.computeIfAbsent("Notebook.sessions", new Function<String, Object>() {
            @Override
            Object apply(String s) {
                return new ConcurrentHashMap<Integer, LivySession>()
            }
        }) as ConcurrentMap<Integer, LivySession>
    }

    private static class LivySession {
        private static final Log logger = LogFactory.getLog(LivySession.class)

        int sessionId
        String applicationId

        Map livy

        final BlockingQueue<Callable<Void>> paragraphs = new LinkedBlockingQueue<>()
        final ExecutorService executor = Executors.newSingleThreadExecutor()

        LivySession(Map livy) {
            logger.info("Create new Livy session")
            this.livy = livy
            ContextSvc ctx = Context.getCurrent().getContextSvc()
            String branch = GitflowSvc.getCurrentBranch()
            executor.submit(new Runnable() {
                @Override
                void run() {
                    try {
                        ctx.inContext(new Runnable() {
                            @Override
                            void run() {
                                logger.info("Start waiting for submitted paragraphs")
                                Context.current.contextSvc.gitflowSvc.setCurrentBranch(branch)
                                execute()
                            }
                        })
                    } catch (Throwable e) {
                        logger.error("Error start context", e)
                    }
                }
            })
        }

        Map getSessionInfo() {
            return ["sessionId": sessionId,
                    "applicationId": applicationId,
                    "paragraphsInQueue": paragraphs.size(),
                    "livy": livy
            ]
        }

        void submitParagrpah(paragraph, params) {
            logger.info("New paragraph submitted. ${paragraph.databaseEntity.name}")

            // первый запуск для данного notebook
            if (sessionId == 0) {
                sessionId = LivyServer.getSessionId([:], livy)
                applicationId = LivyServer.sessionInfo(livy, [sessionId: sessionId]).result.appId
            } else {
                def info
                try {
                    info = LivyServer.sessionInfo(livy, [sessionId: sessionId])
                } catch (Throwable e) {
                    logger.error("Cannot find session id " + sessionId, e)
                }
                if (info == null || info.result.appId != applicationId || info.result.state == "dead") {
                    logger.error("Invalid session: " + sessionId)
                    paragraph.setErrorResult("Invalid session ${sessionId}. Need to re-evalute all depenedent paragraphs", "", "")
                    sessionId = 0
                    applicationId = null
                }
            }

            paragraphs.put(new Callable<Void>() {
                @Override
                Void call() throws Exception {
                    paragraph.run(livy, sessionId, params)
                    return null
                }
            })
        }

        void execute() {
            while (true) {
                def t = paragraphs.take()
                try {
                    logger.info("Start paragraph executing")
                    t.call()
                    logger.info("Finish paragraph executing")
                } catch (Throwable e) {
                    logger.error("Error while executing paragraph", e)
                }
            }
        }
    }
}
