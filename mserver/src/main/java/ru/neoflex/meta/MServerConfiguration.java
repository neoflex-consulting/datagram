package ru.neoflex.meta;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.eclipse.jgit.http.server.GitServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
/**
 * Created by orlov on 27.03.2015.
 */
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import ru.neoflex.meta.license.LicenseService;
import ru.neoflex.meta.svc.BaseSvc;
import ru.neoflex.meta.utils.FileSystem;
import ru.neoflex.meta.utils.MetaResource;
//import springfox.documentation.builders.PathSelectors;
//import springfox.documentation.builders.RequestHandlerSelectors;
//import springfox.documentation.spi.DocumentationType;
//import springfox.documentation.spring.web.plugins.Docket;
//import springfox.documentation.swagger2.annotations.EnableSwagger2;


@SpringBootApplication
@ImportResource("application-context.xml")
@PropertySource(value="classpath:license.properties", ignoreResourceNotFound=true)
//@EnableSwagger2
public class MServerConfiguration {
    @Value("${gitflow.root:${user.dir}/gitflow}")
    String gitflowRoot;

    public static void main(String[] args) {
        try {
            BaseSvc.getTempDir().mkdirs();
            FileSystem.forceDeleteFolder(BaseSvc.getTempDir().toPath(), true, null);
            List<URL> urlList = new ArrayList<>();
            urlList.add(BaseSvc.getTempDir().toURI().toURL());
            urlList.add(BaseSvc.getDeployDir().toURI().toURL());
            urlList.add(BaseSvc.getMSpaceDir().toURI().toURL());
            urlList.add(BaseSvc.getLibDir().toURI().toURL());
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new URLClassLoader(urlList.toArray(new URL[0]), cl) {
                public URL getResource(String name) {
                    URL resource = findResource(name);
                    if (resource != null) {
                        return resource;
                    }
                    URL jarResource = super.getResource(name);
                    if (jarResource == null) {
                        return null;
                    }
                    if (endsWithAnyIgnoreCase(jarResource.getPath(), "/", ".egx", ".egl", ".eol", ".etl", ".evl")) {
                        return jarResource;
                    }
                    File file = MetaResource.exportURL(jarResource, BaseSvc.getTempDir(), name, true);
                    try {
                        return file.toURI().toURL();
                    } catch (MalformedURLException e) {
                        return jarResource;
                    }
                }

            });
            SpringApplication.run(MServerConfiguration.class, args);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private static boolean endsWithAnyIgnoreCase(String path, String ...suffixes) {
        String pathlc = path.toLowerCase();
        for (String suffix: suffixes) {
            if (pathlc.endsWith(suffix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
    
    @Value("${logging.file}")
    private String loggingFile;
    
    //@Autowired
    //LicenseService licenseService;
    
    @Autowired
    private ApplicationContext appContext;

//    @Bean
//    public Docket api() {
//        return new Docket(DocumentationType.SWAGGER_2)
//                .select()
//                .apis(RequestHandlerSelectors.any())
//                .paths(PathSelectors.any())
//                .build();
//    }
    
    @Bean
    public HttpSessionListener httpSessionListener() {
        return new HttpSessionListener() {
            private Map<String, Integer> sessionCount = new java.util.HashMap<String, Integer>();
            
            @Scheduled(fixedDelay = 1000 * 60 * 60 * 24)
            public void logTime() throws Exception {
                /*if(licenseService.getLicenseProperties() != null) {
                    licenseService.saveSessionLogLine(loggingFile + ".sessions", "TIMER", 0, this.sessionCount.values().stream().reduce(0, (left, right) -> left + right));
                }*/
            }
            
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                UserDetails principal = (UserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                Integer userSessionsCount = this.sessionCount.get(principal.getUsername()) == null ? 0 : this.sessionCount.get(principal.getUsername());                
                this.sessionCount.put(principal.getUsername(), ++userSessionsCount);
                
                Integer sum = this.sessionCount.values().stream().reduce(0, (left, right) -> left + right);
                
                /*if(licenseService.isLogRestriction(userSessionsCount, sum)) {
                    try {
                        licenseService.saveSessionLogLine(loggingFile + ".sessions", principal.getUsername(), userSessionsCount, sum);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    try {
                        if (licenseService.isRestristionActive(licenseService.readLog(Paths.get(loggingFile + ".sessions")))) {
                            ((ConfigurableApplicationContext) appContext).close();
                        }
                    } catch (ParseException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }*/
                
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null) {
                    return;
                }
                UserDetails principal = (UserDetails) authentication.getPrincipal();
                Integer userSessionsCount = this.sessionCount.get(principal.getUsername()) == null ? 0 : this.sessionCount.get(principal.getUsername());
                this.sessionCount.put(principal.getUsername(), --userSessionsCount);

                Integer sum = this.sessionCount.values().stream().reduce(0, (left, right) -> left + right);
                
                /*if(licenseService.isLogRestriction(userSessionsCount, sum)) {
                    try {
                        licenseService.saveSessionLogLine(loggingFile + ".sessions", principal.getUsername(), userSessionsCount, sum);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }*/
            }
        };
    }

    @Bean
    public ServletRegistrationBean servletRegistrationBean(){
        ServletRegistrationBean registration = new ServletRegistrationBean(new GitServlet(),"/git/*");
        Map<String,String> params = new HashMap<>();
        params.put("base-path", gitflowRoot);
        params.put("export-all", "1");
        registration.setInitParameters(params);
        return registration;
    }

}
