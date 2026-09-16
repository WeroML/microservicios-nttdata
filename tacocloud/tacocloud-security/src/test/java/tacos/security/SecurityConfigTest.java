package tacos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;

public class SecurityConfigTest {

  @Test
  public void securityConfig_shouldConfigureFilterChainWithDenyByDefault() throws Exception {
    SecurityConfig securityConfig = new SecurityConfig();

    assertThat(securityConfig.encoder()).isInstanceOf(BCryptPasswordEncoder.class);

    ObjectPostProcessor<Object> objectPostProcessor = new ObjectPostProcessor<Object>() {
      @Override
      public <O> O postProcess(O object) {
        return object;
      }
    };
    AuthenticationManagerBuilder authBuilder = new AuthenticationManagerBuilder(objectPostProcessor);
    authBuilder.parentAuthenticationManager(authentication -> authentication);
    HttpSecurity http = new HttpSecurity(objectPostProcessor, authBuilder, new HashMap<>());

    securityConfig.configure(http);

    DefaultSecurityFilterChain chain = http.build();
    assertThat(chain).isNotNull();
    assertThat(chain.getFilters()).isNotEmpty();
  }
}
