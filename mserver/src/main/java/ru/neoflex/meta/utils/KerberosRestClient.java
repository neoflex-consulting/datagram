package ru.neoflex.meta.utils;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.HttpParams;
import org.springframework.util.StringUtils;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KerberosRestClient extends groovyx.net.http.RESTClient {

    private static final Credentials credentials = new NullCredentials();

    private final String keyTabLocation;
    private final String userPrincipal;

    public KerberosRestClient(Object defaultURI, String keyTabLocation, String userPrincipal) throws URISyntaxException {
        super(defaultURI);
        this.keyTabLocation = keyTabLocation;
        this.userPrincipal = userPrincipal;
    }

    @Override
    protected HttpClient createClient(HttpParams params ) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        Lookup<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider> create()
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true)).build();
        builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(null, -1, null), credentials);
        builder.setDefaultCredentialsProvider(credentialsProvider);
        CloseableHttpClient httpClient = builder.build();
        return httpClient;
    }

    @Override
    protected Object doRequest( final RequestConfigDelegate delegate ) throws IOException {
        try {
            ClientLoginConfig loginConfig = new ClientLoginConfig(keyTabLocation, userPrincipal);
            Set<Principal> princ = new HashSet<Principal>(1);
            princ.add(new KerberosPrincipal(userPrincipal));
            Subject sub = new Subject(false, princ, new HashSet<Object>(), new HashSet<Object>());
            LoginContext lc = new LoginContext("", sub, null, loginConfig);
            lc.login();
            Subject serviceSubject = lc.getSubject();
            return Subject.doAs(serviceSubject, new PrivilegedExceptionAction<Object>() {

                @Override
                public Object run() throws IOException {
                    return KerberosRestClient.this.doRequestSubject(delegate);
                }
            });

        } catch (Exception e) {
            throw new RuntimeException("Error running rest call", e);
        }
    }

    private Object doRequestSubject(RequestConfigDelegate delegate) throws IOException {
        return super.doRequest(delegate);
    }

    private static class ClientLoginConfig extends Configuration {

        private final String keyTabLocation;
        private final String userPrincipal;

        public ClientLoginConfig(String keyTabLocation, String userPrincipal) {
            super();
            this.keyTabLocation = keyTabLocation;
            this.userPrincipal = userPrincipal;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {

            Map<String, Object> options = new HashMap<String, Object>();

            if (!StringUtils.hasText(keyTabLocation) || !StringUtils.hasText(userPrincipal)) {
                // cache
                options.put("useTicketCache", "true");
            } else {
                // keytab
                options.put("useKeyTab", "true");
                options.put("keyTab", this.keyTabLocation);
                options.put("principal", this.userPrincipal);
                options.put("storeKey", "true");
            }
            options.put("doNotPrompt", "true");
            options.put("isInitiator", "true");

            return new AppConfigurationEntry[] { new AppConfigurationEntry(
                    "com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options) };
        }

    }

    private static class NullCredentials implements Credentials {

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

    }
}
