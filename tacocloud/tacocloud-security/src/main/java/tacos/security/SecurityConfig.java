package tacos.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation
             .authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web
             .builders.HttpSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web
                        .configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("deprecation")
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
  
  @Autowired
  private UserDetailsService userDetailsService;
  
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
      .authorizeRequests()
        // Preflight CORS para frontend (Angular)
        .antMatchers(HttpMethod.OPTIONS).permitAll()
        
        // Catálogo público de solo lectura
        .antMatchers(HttpMethod.GET, "/api/ingredients/**", "/api/tacos/**").permitAll()
        
        // Páginas y recursos públicos
        .antMatchers("/", "/login", "/register", "/styles/**", "/images/**", "/static/**").permitAll()
        
        // Roles útiles: Modificación de ingredientes protegida por roles
        .antMatchers(HttpMethod.POST, "/api/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.PUT, "/api/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.PATCH, "/api/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.DELETE, "/api/ingredients/**").hasAnyRole("ADMIN", "USER")
        
        // Roles útiles: Creación y gestión de órdenes y tacos requiere ROLE_USER
        .antMatchers("/api/orders/**").hasRole("USER")
        .antMatchers(HttpMethod.POST, "/api/tacos/**").hasRole("USER")
        
        // Principio DENY-BY-DEFAULT: Cualquier otra ruta exige autenticación
        .anyRequest().authenticated()
        
      .and()
        .formLogin()
          .loginPage("/login")
          
      .and()
        .httpBasic()
          .realmName("Taco Cloud")
          
      .and()
        .logout()
          .logoutSuccessUrl("/")
          
      .and()
        .csrf()
          .ignoringAntMatchers("/h2-console/**", "/api/**")

      // Allow pages to be loaded in frames from the same origin; needed for H2-Console
      .and()  
        .headers()
          .frameOptions()
            .sameOrigin()
      ;
  }

  @Bean
  public PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }
  
  
  @Override
  protected void configure(AuthenticationManagerBuilder auth)
      throws Exception {

    auth
      .userDetailsService(userDetailsService)
      .passwordEncoder(encoder());
    
  }

}
