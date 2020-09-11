package ru.neoflex.meta.mserver;

/**
 * Created by orlov on 12.08.2016.
 */

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@Order(1)
public class MDCIPFilter implements Filter {

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        MDC.put("IP", req.getRemoteAddr());
        chain.doFilter(req, res);
        MDC.remove("IP");
    }

    public void init(FilterConfig filterConfig) {}

    public void destroy() {}

}
