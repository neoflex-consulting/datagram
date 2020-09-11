package ru.neoflex.meta.mserver;

/**
 * Created by orlov on 12.08.2016.
 */
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

@Component
@Order(10)
public class MDCUserNameFilter implements Filter {

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            MDC.put("user", authentication.getName());
        }
        chain.doFilter(req, res);
        MDC.remove("user");
    }

    public void init(FilterConfig filterConfig) {}

    public void destroy() {}

}
