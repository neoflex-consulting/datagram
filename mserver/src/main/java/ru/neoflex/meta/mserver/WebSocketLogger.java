package ru.neoflex.meta.mserver;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.CoreConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebSocketLogger implements GenericApplicationListener {
    private final static Log logger = LogFactory.getLog(WebSocketLogger.class);
    private static SimpleDateFormat jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private boolean addedCustomAppender;
    @Autowired
    public SimpMessageSendingOperations messagingTemplate;

    @Override
    public boolean supportsEventType(ResolvableType resolvableType) {
        return ApplicationPreparedEvent.class.isAssignableFrom(resolvableType.getRawClass());
    }

    @Override
    public boolean supportsSourceType(Class<?> aClass) {
        return true;
    }

    private String formatThrowable(IThrowableProxy iThrowableProxy) {
        IThrowableProxy cause = iThrowableProxy.getCause();
        if (cause != null) {
            return formatThrowable(cause);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(iThrowableProxy.getClassName());
        String message = iThrowableProxy.getMessage();
        if (message != null) {
            builder.append(": ").append(message);
        }
        builder.append(CoreConstants.LINE_SEPARATOR);
        for (StackTraceElementProxy step : iThrowableProxy.getStackTraceElementProxyArray()) {
            String string = step.toString();
            builder.append(CoreConstants.TAB).append(string);
            ThrowableProxyUtil.subjoinPackagingData(builder, step);
            builder.append(CoreConstants.LINE_SEPARATOR);
        }
        return builder.toString();
    }
    @Override
    @EventListener
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        if (!addedCustomAppender) {
            final Appender<ILoggingEvent> newAppender = new AppenderBase<ILoggingEvent>() {
                @Override
                protected void append(ILoggingEvent iLoggingEvent) {
                    String user = MDC.get("user");
                    //Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    //String user = authentication.getName();
                    if (user != null && user.length() > 0) {
                        Map info = new HashMap();
                        info.put("message", iLoggingEvent.getFormattedMessage());
                        info.put("level", iLoggingEvent.getLevel().toString());
                        info.put("timestamp", jsonTimestampParser.format(new Date(iLoggingEvent.getTimeStamp())));
                        IThrowableProxy iThrowableProxy = iLoggingEvent.getThrowableProxy();
                        if (iThrowableProxy != null) {
                            info.put("stacktrace", formatThrowable(iThrowableProxy));
                        }
                        messagingTemplate.convertAndSendToUser(user, "/queue/log", info);
                    }
                }
            };
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            final ch.qos.logback.classic.Logger root = context.getLogger("ROOT");
            newAppender.setName("WebSocket Appender");
            newAppender.setContext(context);
            newAppender.start();
            root.addAppender(newAppender);
            logger.info("Added custom WebSocket appender");
            addedCustomAppender = true;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 21;
    }
}
