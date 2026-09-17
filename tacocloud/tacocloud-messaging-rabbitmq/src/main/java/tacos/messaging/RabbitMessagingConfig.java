package tacos.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMessagingConfig {

  @Bean("rabbitMessageConverter")
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

}
