package rw.animalproduct.animal.production.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Set;

public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess
            (HttpServletRequest request,
             HttpServletResponse response,
             Authentication authentication) throws IOException,
            ServletException {
       Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities());

       if(roles.contains("ROLE_ADMIN")){
           response.sendRedirect("/admin/dashboard");
       }else if(roles.contains("ROLE_REGULAR_USER")){
           response.sendRedirect("/user/dashboard");
       }else{
           response.sendRedirect("/");
       }
           }

}
