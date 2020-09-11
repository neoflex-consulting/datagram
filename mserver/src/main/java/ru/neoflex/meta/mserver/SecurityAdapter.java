package ru.neoflex.meta.mserver;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.authentication.ad.ActiveDirectoryLdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ru.neoflex.meta.svc.UserDetailSvc;

@Configuration
@EnableWebSecurity
public class SecurityAdapter extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.sessionManagement().sessionFixation().none();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedMethod("HEAD");
        config.addAllowedMethod("GET");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        source.registerCorsConfiguration("/**", config);
        http
            .httpBasic().and()
            .authorizeRequests()
            .antMatchers("/cim/MetaServer/**").permitAll()
            .antMatchers("/psm/ui/**").permitAll()
            .antMatchers("/cim/transformation/**", "/cim/workflow/**", "/cim/dbmonitor/**").permitAll()
            .antMatchers("/api/*/copy/**").hasAnyAuthority("ADMIN")
            .antMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority("ADMIN")
            .antMatchers(HttpMethod.POST, "/api/*/rt.WorkflowDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers(HttpMethod.POST, "/api/*/rt.CoordinatorDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers(HttpMethod.POST, "/api/*/rt.TransformationDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/api/operation/*/rt/WorkflowDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/api/operation/*/rt/CoordinatorDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/api/operation/*/rt/TransformationDeployment/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/api/operation/*/rt/LivyServer/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/api/operation/**").hasAnyAuthority("ADMIN")
            .antMatchers(HttpMethod.GET, "/api/**").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority("ADMIN")
            .antMatchers("/system/user/**").authenticated()
            .antMatchers("/system/**").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/api/**").denyAll()
            .antMatchers(HttpMethod.GET, "/admin2/**").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers(HttpMethod.PUT, "/admin2/**").hasAnyAuthority("ADMIN", "OPERATOR")
            .antMatchers("/logfile").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/info").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/metrics").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/env").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/health").hasAnyAuthority("ADMIN", "OPERATOR", "USER")
            .antMatchers("/git/**").authenticated()
            .and().cors().configurationSource(source).and()
            .logout().logoutRequestMatcher(new AntPathRequestMatcher("/logout")).deleteCookies("JSESSIONID").clearAuthentication(true).invalidateHttpSession(true).logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK)).and()
            .csrf().disable();
    }

	@Configuration
	@ConditionalOnProperty(prefix = "ldap", name = "enabled", havingValue = "false")
	protected static class BaseConfiguration extends
			GlobalAuthenticationConfigurerAdapter {
		
		@Autowired
		private UserDetailSvc userDetailService;
		
		@Override
		public void init(AuthenticationManagerBuilder auth) throws Exception {
			auth.eraseCredentials(false).userDetailsService(userDetailService);
		}
	} 
    
	@Configuration
	@ConditionalOnProperty(prefix = "ldap", name = "enabled", matchIfMissing = true)
	@PropertySource("file:${ldap.config:${user.dir}/ldap.properties}")
	protected static class LdapConfiguration extends
			GlobalAuthenticationConfigurerAdapter {

		@Value("${ldap.host}")
		private String host;
		@Value("${ldap.domain}")
		private String domain;
		@Value("${ldap.port}")
		private String port;
        @Value("${ldap.base}")
        private String base;
        @Value("${ldap.searchFilter:}")
        private String searchFilter;
        @Value("${ldap.admin_group:DG_ADMIN}")
        private String adminGroup;
        @Value("${ldap.operator_group:DG_OPERATOR}")
        private String operatorGroup;
        @Value("${ldap.user_group:DG_USER}")
        private String userGroup;
        @Value("${ldap.always_admin:false}")
        private Boolean alwaysAdmin;

		@Override
		public void init(AuthenticationManagerBuilder auth) throws Exception {
			final String url = "ldap://" + host + ":" + port;
			final ActiveDirectoryLdapAuthenticationProvider provider =
                    new ActiveDirectoryLdapAuthenticationProvider(domain, url, base);
			if (StringUtils.hasText(searchFilter)) {
			    provider.setSearchFilter(searchFilter);
            }
			final LdapUserDetailsMapper ldapUserDetailsMapper = new LdapUserDetailsMapper();
			provider.setUserDetailsContextMapper(new UserDetailsContextMapper() {
                @Override
                public UserDetails mapUserFromContext(DirContextOperations ctx, String username, Collection<? extends GrantedAuthority> authorities) {
                    UserDetails userDetails = ldapUserDetailsMapper.mapUserFromContext(ctx, username, authorities);
                    Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
                    if (alwaysAdmin) {
                        grantedAuthorities.add(new SimpleGrantedAuthority("ADMIN"));
                    }
                    else {
                        for (GrantedAuthority grantedAuthority: userDetails.getAuthorities()) {
                            if (adminGroup.equals(grantedAuthority.getAuthority())) {
                                grantedAuthorities.add(new SimpleGrantedAuthority("ADMIN"));
                            } else
                            if (operatorGroup.equals(grantedAuthority.getAuthority())) {
                                grantedAuthorities.add(new SimpleGrantedAuthority("OPERATOR"));
                            } else
                            if (userGroup.equals(grantedAuthority.getAuthority())) {
                                grantedAuthorities.add(new SimpleGrantedAuthority("USER"));
                            } else {
                                grantedAuthorities.add(new SimpleGrantedAuthority(grantedAuthority.getAuthority()));
                            }                                
                        }
                    }
                    LdapUserDetailsImpl.Essence essence = new LdapUserDetailsImpl.Essence((LdapUserDetails) userDetails);
                    essence.setAuthorities(grantedAuthorities);
                    return essence.createUserDetails();
                }

                @Override
                public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
                    ldapUserDetailsMapper.mapUserToContext(user, ctx);
                }
            });

			auth.eraseCredentials(false).authenticationProvider(provider);
        }
    }
    
    private CsrfTokenRepository csrfTokenRepository() {
      HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
      repository.setHeaderName("X-XSRF-TOKEN");
      return repository;
    }
    
}
