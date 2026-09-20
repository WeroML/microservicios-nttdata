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
        
        // Ejercicio 35: Versionar la API y publicar contrato OpenAPI
        // Contrato OpenAPI, Swagger UI y Metadatos de versiones públicos
        .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/api/openapi.*", "/api/versions").permitAll()

        // Catálogo público de solo lectura (Ejercicio 13 & 15 & 35: Catálogo, Cupones y Versionado)
        .antMatchers(HttpMethod.GET,
            "/api/ingredients/**", "/api/v1/ingredients/**",
            "/api/tacos/**", "/api/v1/tacos/**",
            "/api/catalog/**", "/api/v1/catalog/**",
            "/api/coupons/**", "/api/v1/coupons/**").permitAll()
        
        // Páginas y recursos públicos
        .antMatchers("/", "/login", "/register", "/styles/**", "/images/**", "/static/**").permitAll()
        
        // Ejercicio 33 & 35: Reemplazar Notes por anuncios operativos seguros (v1 y legacy)
        .antMatchers(HttpMethod.POST, "/actuator/announcements/**", "/actuator/notes/**").hasAnyRole("ADMIN", "OPERATOR")
        .antMatchers(HttpMethod.DELETE, "/actuator/announcements/**", "/actuator/notes/**").hasAnyRole("ADMIN", "OPERATOR")
        .antMatchers(HttpMethod.GET, "/api/announcements/**", "/api/v1/announcements/**").permitAll()
        .antMatchers(HttpMethod.POST, "/api/announcements/**", "/api/v1/announcements/**").hasAnyRole("ADMIN", "OPERATOR")
        .antMatchers(HttpMethod.DELETE, "/api/announcements/**", "/api/v1/announcements/**").hasAnyRole("ADMIN", "OPERATOR")

        // Ejercicio 32: Métricas y salud que explican el negocio
        .antMatchers("/actuator/**", "/api/business/**").permitAll()
        
        // Roles útiles: Modificación de ingredientes protegida por roles
        .antMatchers(HttpMethod.POST, "/api/ingredients/**", "/api/v1/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.PUT, "/api/ingredients/**", "/api/v1/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.PATCH, "/api/ingredients/**", "/api/v1/ingredients/**").hasAnyRole("ADMIN", "USER")
        .antMatchers(HttpMethod.DELETE, "/api/ingredients/**", "/api/v1/ingredients/**").hasAnyRole("ADMIN", "USER")
        
        // Roles útiles: Creación y gestión de órdenes (USER y ADMIN para cocina y estados)
        // Ejercicio 25: Flujo de estados de una orden
        // Ejercicio 35: Versionado v1 y v2 de órdenes
        .antMatchers("/api/orders/**", "/api/v1/orders/**", "/api/v2/orders/**").hasAnyRole("USER", "ADMIN")
        .antMatchers(HttpMethod.POST, "/api/tacos/**", "/api/v1/tacos/**").hasRole("USER")
        // Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
        .antMatchers("/api/favorites/**", "/api/v1/favorites/**").hasRole("USER")
        // Ejercicio 22: Calificaciones y ranking de tacos
        .antMatchers(HttpMethod.DELETE, "/api/tacos/**", "/api/v1/tacos/**").hasRole("USER")
        // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
        .antMatchers("/api/kitchen/**", "/api/v1/kitchen/**").hasRole("ADMIN")
        // Ejercicio 28: Elegir broker en runtime, no editando el POM
        .antMatchers("/api/messaging/**", "/api/v1/messaging/**").hasRole("ADMIN")
        // Ejercicio 29: Outbox transaccional para no perder órdenes
        .antMatchers("/api/outbox/**", "/api/v1/outbox/**").hasRole("ADMIN")
        
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
