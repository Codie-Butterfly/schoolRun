package com.codiebutterfly.schoolrun;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
@Configuration public class Security {
 @Bean SecurityFilterChain chain(HttpSecurity http,Store s)throws Exception{
  var filter=new OncePerRequestFilter(){
   protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    String a=request.getHeader("Authorization");
    if(a!=null){try{var u=s.user(a);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.get("id"),null,List.of()));}catch(ResponseStatusException e){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Session expired. Please sign in again.\"}");return;}}
    chain.doFilter(request,response);
   }
  };
  return http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
   .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Please sign in\"}");}))
   .authorizeHttpRequests(c->c.requestMatchers("/api/health","/api/auth/register","/api/auth/login","/api/paynow/result","/api/paynow/return","/error").permitAll().anyRequest().authenticated())
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
}
