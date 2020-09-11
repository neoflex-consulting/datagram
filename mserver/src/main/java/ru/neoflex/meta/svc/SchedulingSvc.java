package ru.neoflex.meta.svc;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.*;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.model.Database;
import ru.neoflex.meta.utils.Context;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

@Service("ru.neoflex.meta.svc.SchedulingSvc")
@DependsOn({
        "ru.neoflex.meta.svc.TeneoSvc",
        "ru.neoflex.meta.svc.GitflowSvc",
        "ru.neoflex.meta.svc.ContextSvc"
})
public class SchedulingSvc extends BaseSvc {
    public final static String CANCELLED = "cancelled";
    public final static String SCHEDULED = "scheduled";

    private final static Log logger = LogFactory.getLog(SchedulingSvc.class);
    @Autowired
    private TaskScheduler taskScheduler;
    @Autowired
    private ContextSvc contextSvc;
    private Map<Long, ScheduledFuture> scheduledTasks = new HashMap<>();

    @PostConstruct
    void init() {
        refreshScheduler();
    }
    public synchronized Map refreshScheduler() {
        Map<Long, ScheduledFuture> newTasks = new HashMap<>();
        final Map<String, Integer> changes = new HashMap(){{
            put(CANCELLED, 0);
            put(SCHEDULED, 0);
        }};
        contextSvc.inContext(new Runnable() {
            @Override
            public void run() {
                Database database = Database.getNew();
                for (Map scheduledTask : database.list("rt.ScheduledTask")) {
                    Boolean enabled = (Boolean) scheduledTask.get("enabled");
                    Long e_id = (Long) scheduledTask.get("e_id");
                    if (enabled == true) {
                        ScheduledFuture scheduledFuture = scheduledTasks.get(e_id);
                        if (scheduledFuture == null) {
                            String name = (String) scheduledTask.get("name");
                            String entityType = (String) scheduledTask.get("entityType");
                            String[] parts = entityType.split("\\.", 2);
                            if (parts.length != 2 || StringUtils.isEmpty(parts[0]) || StringUtils.isEmpty(parts[1])) {
                                logger.error(name + ": Invalid entity type: " + entityType);
                                continue;
                            }
                            String ePackage = parts[0];
                            String eClass = parts[1];
                            String entityName = (String) scheduledTask.get("entityName");
                            String methodName = (String) scheduledTask.get("methodName");
                            Map scheduler = (Map) scheduledTask.get("scheduler");
                            if (scheduler == null) {
                                logger.error(name + ": No scheduler");
                                continue;
                            }
                            Map entity = null;
                            if (StringUtils.isNotEmpty(entityType) && StringUtils.isNotEmpty(entityName)) {
                                Map params = new HashMap();
                                params.put("name", entityName);
                                List<Map> entities = database.list(entityType, params);
                                if (entities.size() == 1) {
                                    entity = entities.get(0);
                                } else {
                                    logger.error(name + ": Entity [" + entityName + "] of type [" + entityType + "] not found");
                                    continue;
                                }
                            }
                            String schedulerType = (String) scheduler.get("_type_");
                            Boolean disableAfterRun = schedulerType.equals("rt.OnceScheduler") && (Boolean) scheduler.get("disableAfterRun") == true;
                            String runAsUser = (String) scheduledTask.get("runAsUser");
                            String runAsPassword = (String) scheduledTask.get("runAsPassword");
                            Context.User user = new Context.User(runAsUser, runAsPassword);
                            Runnable runnable = getRunnable(name, e_id, ePackage, eClass, methodName, entity, disableAfterRun, user);
                            scheduledFuture = scheduleWithPolicy(name, scheduler, entity, schedulerType, runnable);
                            changes.put(SCHEDULED, changes.get(SCHEDULED) + 1);
                            scheduledTask.put("lastScheduleTime", new Date());
                            database.update(scheduledTask);
                            database.commit();
                        }
                        if (scheduledFuture != null) {
                            newTasks.put(e_id, scheduledFuture);
                        }
                    }
                }
            }
        });
        for(Iterator<Map.Entry<Long, ScheduledFuture>> it = scheduledTasks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, ScheduledFuture> entry = it.next();
            if(!newTasks.containsKey(entry.getKey())) {
                entry.getValue().cancel(false);
                changes.put(CANCELLED, changes.get(CANCELLED) + 1);
            }
        }
        scheduledTasks = newTasks;
        return changes;
    }

    private ScheduledFuture scheduleWithPolicy(String name, Map scheduler, Map entity, String schedulerType, Runnable runnable) {
        RetryTemplate retryTemplate = new RetryTemplate();
        BackOffPolicy backOffPolicy = getBackOffPolicy((Map) entity.get("backOffPolicy"));
        retryTemplate.setBackOffPolicy(backOffPolicy);
        RetryPolicy retryPolicy = getRetryPolicy((Map) entity.get("retryPolicy"));
        retryTemplate.setRetryPolicy(retryPolicy);
        ScheduledFuture scheduledFuture = schedule(name, scheduler, schedulerType, new Runnable() {
            @Override
            public void run() {
                retryTemplate.execute(new RetryCallback<Void, RuntimeException>() {
                    @Override
                    public Void doWithRetry(RetryContext context) throws RuntimeException {
                        runnable.run();
                        return null;
                    }
                });
            }
        });
        return scheduledFuture;
    }

    private BackOffPolicy getBackOffPolicy(Map backOffPolicy) {
        if (backOffPolicy != null) {
            String type = (String) backOffPolicy.get("_type_");
            if (StringUtils.isNotEmpty(type)) {
                if (type.equals("rt.NoBackOffPolicy")) {
                    return new NoBackOffPolicy();
                }
                if (type.equals("rt.FixedBackOffPolicy")) {
                    FixedBackOffPolicy result = new FixedBackOffPolicy();
                    Long backOffPeriod = (Long) backOffPolicy.get("backOffPeriod");
                    if (backOffPeriod != null) {
                        result.setBackOffPeriod(backOffPeriod);
                    }
                    return result;
                }
                if (type.equals("rt.ExponentialBackOffPolicy")) {
                    ExponentialBackOffPolicy result = new ExponentialBackOffPolicy();
                    Long initialInterval = (Long) backOffPolicy.get("initialInterval");
                    if (initialInterval != null) {
                        result.setInitialInterval(initialInterval);
                    }
                    Long maxInterval = (Long) backOffPolicy.get("maxInterval");
                    if (maxInterval != null) {
                        result.setMaxInterval(maxInterval);
                    }
                    Double multiplier = (Double) backOffPolicy.get("multiplier");
                    if (multiplier != null) {
                        result.setMultiplier(multiplier);
                    }
                    return result;
                }
                if (type.equals("rt.ExponentialRandomBackOffPolicy")) {
                    ExponentialRandomBackOffPolicy result = new ExponentialRandomBackOffPolicy();
                    Long initialInterval = (Long) backOffPolicy.get("initialInterval");
                    if (initialInterval != null) {
                        result.setInitialInterval(initialInterval);
                    }
                    Long maxInterval = (Long) backOffPolicy.get("maxInterval");
                    if (maxInterval != null) {
                        result.setMaxInterval(maxInterval);
                    }
                    Double multiplier = (Double) backOffPolicy.get("multiplier");
                    if (multiplier != null) {
                        result.setMultiplier(multiplier);
                    }
                    return result;
                }
                if (type.equals("rt.UniformRandomBackOffPolicy")) {
                    UniformRandomBackOffPolicy result = new UniformRandomBackOffPolicy();
                    Long minBackOffPeriod = (Long) backOffPolicy.get("minBackOffPeriod");
                    if (minBackOffPeriod != null) {
                        result.setMinBackOffPeriod(minBackOffPeriod);
                    }
                    Long maxBackOffPeriod = (Long) backOffPolicy.get("maxBackOffPeriod");
                    if (maxBackOffPeriod != null) {
                        result.setMaxBackOffPeriod(maxBackOffPeriod);
                    }
                    return result;
                }
            }
        }
        return new NoBackOffPolicy();
    }

    private RetryPolicy getRetryPolicy(Map retryPolicy) {
        if (retryPolicy != null) {
            String type = (String) retryPolicy.get("_type_");
            if (StringUtils.isNotEmpty(type)) {
                if (type.equals("rt.NeverRetryPolicy")) {
                    return new NeverRetryPolicy();
                }
                if (type.equals("rt.AlwaysRetryPolicy")) {
                    return new AlwaysRetryPolicy();
                }
                if (type.equals("rt.TimeoutRetryPolicy")) {
                    TimeoutRetryPolicy result = new TimeoutRetryPolicy();
                    Long timeout = (Long) retryPolicy.get("timeout");
                    if (timeout != null) {
                        result.setTimeout(timeout);
                    }
                    return result;
                }
                if (type.equals("rt.SimpleRetryPolicy")) {
                    Integer maxAttempts = (Integer) retryPolicy.get("maxAttempts");
                    if (maxAttempts == null) {
                        maxAttempts = SimpleRetryPolicy.DEFAULT_MAX_ATTEMPTS;
                    }
                    Map<Class<? extends Throwable>, Boolean> res = new HashMap<>();
                    List<Map> retryableExceptions = (List<Map>) retryPolicy.get("retryableExceptions");
                    if (retryableExceptions.size() == 0) {
                        res = Collections.singletonMap(Exception.class, true);
                    }
                    else {
                        for (Map re: retryableExceptions) {
                            String exceptionClass = (String) re.get("exceptionClass");
                            try {
                                res.put((Class<? extends Throwable>) Class.forName(exceptionClass), (Boolean) re.get("retryable") == true);
                            }
                            catch (Throwable t) {
                                logger.error("Throwable class " + exceptionClass + " not found", t);
                            }
                        }
                    }
                    return new SimpleRetryPolicy(maxAttempts, res);
                }
            }
        }
        return new NeverRetryPolicy();
    }

    private ScheduledFuture schedule(String name, Map scheduler, String schedulerType, Runnable runnable) {
        ScheduledFuture scheduledFuture = null;
        if (schedulerType.equals("rt.DelayScheduler")) {
            Date startTime = (Date) scheduler.get("startTime");
            Long delay = (Long) scheduler.get("delay");
            if (delay == null) {
                logger.error(name + ": Delay undefined");
                return null;
            }
            if (startTime == null) {
                scheduledFuture = taskScheduler.scheduleWithFixedDelay(runnable, delay);
            } else {
                scheduledFuture = taskScheduler.scheduleWithFixedDelay(runnable, startTime, delay);
            }
        } else if (schedulerType.equals("rt.OnceScheduler")) {
            Date startTime = (Date) scheduler.get("startTime");
            if (startTime == null) {
                scheduledFuture = taskScheduler.schedule(runnable, new Date());
            } else {
                scheduledFuture = taskScheduler.schedule(runnable, startTime);
            }
        } else if (schedulerType.equals("rt.PeriodScheduler")) {
            Date startTime = (Date) scheduler.get("startTime");
            Long period = (Long) scheduler.get("period");
            if (period == null) {
                logger.error(name + ": Period undefined");
                return null;
            }
            if (startTime == null) {
                scheduledFuture = taskScheduler.scheduleAtFixedRate(runnable, period);
            } else {
                scheduledFuture = taskScheduler.scheduleAtFixedRate(runnable, startTime, period);
            }
        } else if (schedulerType.equals("rt.CronScheduler")) {
            String cronExpression = (String) scheduler.get("cronExpression");
            if (StringUtils.isEmpty(cronExpression)) {
                logger.error(name + ": cronExpression undefined");
                return null;
            }
            scheduledFuture = taskScheduler.schedule(runnable, new CronTrigger(cronExpression));
        }
        if (scheduledFuture == null) {
            logger.error(name + ": Unknown scheduler type");
            return null;
        }
        return scheduledFuture;
    }

    private Runnable getRunnable(String name, Long e_id, String ePackage, String eClass, String methodName, Map entity, Boolean disableAfterRun, Context.User user) {
        return new Runnable() {
            @Override
            public void run() {
                contextSvc.inContext(new Runnable() {
                    @Override
                    public void run() {
                        logger.info("Run scheduled task: " + name);
                        Context.getCurrent().setUser(user);
                        Database database = Database.getNew();
                        Map scheduledTask = database.get("rt.ScheduledTask", e_id);
                        try {
                            Map requestParams = new HashMap();
                            contextSvc.getScriptSvc().runMethod("MetaServer/" + ePackage + "/" + eClass + ".groovy", methodName, entity, requestParams);
                            if (disableAfterRun) {
                                scheduledTask.put("enabled", false);
                            }
                            scheduledTask.put("lastRunTime", new Date());
                            database.update(scheduledTask);
                            database.commit();
                        } catch (Throwable t) {
                            scheduledTask.put("lastErrorTime", new Date());
                            scheduledTask.put("lastError", ExceptionUtils.getStackTrace(t));
                            database.update(scheduledTask);
                            database.commit();
                            throw t;
                        }
                    }
                });

            }
        };
    }

}
