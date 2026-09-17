package tacos.messaging;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Service("orderMessagingRouter")
@Primary
@Slf4j
public class DynamicOrderMessagingRouter implements OrderMessagingService {

  public static final String BROKER_NOOP = "noop";
  public static final String BROKER_JMS = "jms";
  public static final String BROKER_RABBITMQ = "rabbitmq";
  public static final String BROKER_KAFKA = "kafka";

  private final Map<String, OrderMessagingService> brokers = new ConcurrentHashMap<>();
  private volatile String activeBroker = BROKER_NOOP;

  public DynamicOrderMessagingRouter() {
    this.activeBroker = BROKER_NOOP;
  }

  public DynamicOrderMessagingRouter(
      OrderMessagingService noopService,
      OrderMessagingService jmsService,
      OrderMessagingService rabbitService,
      OrderMessagingService kafkaService) {
    this(null, null, noopService, jmsService, rabbitService, kafkaService);
  }

  @Autowired
  public DynamicOrderMessagingRouter(
      @Value("${tacocloud.messaging.broker:}") String configuredBroker,
      @Autowired(required = false) Environment env,
      @Autowired(required = false) @Qualifier("noopOrderMessagingService") OrderMessagingService noopService,
      @Autowired(required = false) @Qualifier("jmsOrderMessagingService") OrderMessagingService jmsService,
      @Autowired(required = false) @Qualifier("rabbitOrderMessagingService") OrderMessagingService rabbitService,
      @Autowired(required = false) @Qualifier("kafkaOrderMessagingService") OrderMessagingService kafkaService) {

    if (noopService != null) brokers.put(BROKER_NOOP, noopService);
    if (jmsService != null) brokers.put(BROKER_JMS, jmsService);
    if (rabbitService != null) brokers.put(BROKER_RABBITMQ, rabbitService);
    if (kafkaService != null) brokers.put(BROKER_KAFKA, kafkaService);

    // Si no se inyectó ningún noop pero la lista está vacía, creamos uno de respaldo
    if (!brokers.containsKey(BROKER_NOOP)) {
      brokers.put(BROKER_NOOP, new NoOpOrderMessagingService());
    }

    String initial = determineInitialBroker(configuredBroker, env);
    setActiveBroker(initial);
    log.info("// Ejercicio 28: DynamicOrderMessagingRouter inicializado. Broker activo: '{}', Disponibles: {}",
        this.activeBroker, brokers.keySet());
  }

  public static String normalizeBrokerName(String name) {
    if (name == null) {
      return BROKER_NOOP;
    }
    String clean = name.trim().toLowerCase();
    switch (clean) {
      case "noop":
      case "none":
      case "mock":
      case "test":
      case "default":
        return BROKER_NOOP;
      case "jms":
      case "artemis":
      case "activemq":
        return BROKER_JMS;
      case "rabbitmq":
      case "rabbit":
      case "amqp":
        return BROKER_RABBITMQ;
      case "kafka":
        return BROKER_KAFKA;
      default:
        return clean;
    }
  }

  private String determineInitialBroker(String configuredBroker, Environment env) {
    if (configuredBroker != null && !configuredBroker.trim().isEmpty()) {
      return normalizeBrokerName(configuredBroker);
    }
    if (env != null) {
      for (String profile : env.getActiveProfiles()) {
        String normalized = normalizeBrokerName(profile);
        if (brokers.containsKey(normalized)) {
          return normalized;
        }
      }
    }
    return BROKER_NOOP;
  }

  public synchronized String setActiveBroker(String brokerName) {
    String normalized = normalizeBrokerName(brokerName);
    if (!brokers.containsKey(normalized)) {
      throw new IllegalArgumentException("Broker no soportado: '" + brokerName + "'. Disponibles: " + getAvailableBrokers());
    }
    String previous = this.activeBroker;
    this.activeBroker = normalized;
    log.info("// Ejercicio 28: Broker de mensajería conmutado en runtime: '{}' -> '{}'", previous, this.activeBroker);
    return this.activeBroker;
  }

  public String getActiveBroker() {
    return this.activeBroker;
  }

  public Set<String> getAvailableBrokers() {
    return Collections.unmodifiableSet(brokers.keySet());
  }

  public OrderMessagingService getActiveService() {
    OrderMessagingService service = brokers.get(activeBroker);
    if (service == null) {
      service = brokers.get(BROKER_NOOP);
    }
    return service;
  }

  public OrderMessagingService getBrokerService(String brokerName) {
    return brokers.get(normalizeBrokerName(brokerName));
  }

  public void registerBroker(String name, OrderMessagingService service) {
    if (name != null && service != null) {
      brokers.put(normalizeBrokerName(name), service);
    }
  }

  @Override
  public void sendOrder(TacoOrder order) {
    OrderMessagingService active = getActiveService();
    if (active != null) {
      log.info("// Ejercicio 28: Enrutando sendOrder a broker '{}' (orden: {})",
          this.activeBroker, order != null ? order.getId() : null);
      active.sendOrder(order);
    } else {
      log.warn("// Ejercicio 28: No hay broker activo disponible para enviar orden");
    }
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    OrderMessagingService active = getActiveService();
    if (active != null) {
      log.info("// Ejercicio 28: Enrutando sendOrderEvent a broker '{}' (evento: [id={}, type={}])",
          this.activeBroker,
          event != null ? event.getEventId() : null,
          event != null ? event.getEventType() : null);
      active.sendOrderEvent(event);
    } else {
      log.warn("// Ejercicio 28: No hay broker activo disponible para enviar evento canónico de orden");
    }
  }

}
