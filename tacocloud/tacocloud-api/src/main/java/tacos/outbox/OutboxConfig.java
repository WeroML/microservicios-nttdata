package tacos.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "tacocloud.outbox.scheduling.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxConfig {

}
