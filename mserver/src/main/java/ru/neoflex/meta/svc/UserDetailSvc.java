package ru.neoflex.meta.svc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.neoflex.meta.controllers.EntityController;
import ru.neoflex.meta.utils.Common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserDetailSvc extends BaseSvc implements UserDetailsService {
    
    @Autowired
    EntityController entityController;
    
    private String getPassword (String login, String password) {
        return Common.getDecryptedPassword("auth.UserInfo.${login}.password", password);
    }
    @Value("${ldap.always_admin:false}")
    private Boolean alwaysAdmin;

    @Override
    public UserDetails loadUserByUsername(String arg0) throws UsernameNotFoundException {
        
        Map<String, Object> params = new HashMap<>();
        List<Map> user = (List<Map>) entityController.select("teneo", "select u from auth.UserInfo u where u.login='" + arg0  + "'", params);
        if(user == null){
            return null;
        }
        Map<String, Object> userMap = user.get(0);
        String[] roles = (String[]) userMap.get("userRoles");
        if(roles == null) {
            roles = new String[0];
        }
        List<GrantedAuthority> au = new ArrayList<GrantedAuthority>();
        if(alwaysAdmin == true) {
            au.add(new SimpleGrantedAuthority("ADMIN"));
        } else {
            for(String role: roles) {
                au.add(new SimpleGrantedAuthority(role));
            }
        }
        UserDetails userDetails = new User(arg0, getPassword((String) userMap.get("login"), (String) userMap.get("password")), true, true, true, true,
            au);
        return userDetails;
    }

}
