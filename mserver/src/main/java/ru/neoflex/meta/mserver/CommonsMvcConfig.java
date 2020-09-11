package ru.neoflex.meta.mserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Created by orlov on 07.06.2015.
 */
@Configuration
@EnableWebMvc
public class CommonsMvcConfig extends WebMvcConfigurerAdapter {
    @Value("${root-path:/index.html}")
    private String rootPath;

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.favorPathExtension(true);
    }    

    @Override
    public void configureDefaultServletHandling(
            DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("redirect:" + rootPath);
    }}
