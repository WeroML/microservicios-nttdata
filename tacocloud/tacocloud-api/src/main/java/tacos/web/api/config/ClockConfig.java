package tacos.web.api.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Ejercicio 20: Taco del día determinista y comprobable
@Configuration
public class ClockConfig {

  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
