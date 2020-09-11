package ru.neoflex.meta.controllers;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by orlov on 08.04.2015.
 */
@ControllerAdvice
public class ExceptionHandlingController {
    private final static Log logger = LogFactory.getLog(ExceptionHandlingController.class);

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    public @ResponseBody Map handleError(HttpServletRequest req, Throwable exception) {
        logger.error(req.getRequestURI(), exception);
        //exception.printStackTrace();
        Map errInfo = new HashMap();
        errInfo.put("url", req.getRequestURL());
        List<String> msgs = new LinkedList<>();
        while (exception != null) {
            String message = exception.getMessage();
            if (message == null) {
                message = exception.toString();
            }
            msgs.add(message);
            exception = exception.getCause();
        }
        errInfo.put("messages", msgs);
        errInfo.put("message", msgs.size() > 0 ? msgs.get(msgs.size() - 1) : "Unknown error");
        return errInfo;
    }}
