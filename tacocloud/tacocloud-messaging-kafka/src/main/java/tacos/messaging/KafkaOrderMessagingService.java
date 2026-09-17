package tacos.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Service("kafkaOrderMessagingService")
@Slf4j
public class KafkaOrderMessagingService implements OrderMessagingService {
  
  private KafkaTemplate kafkaTemplate;

  public KafkaOrderMessagingService() {
    this(null);
  }
  
  @Autowired
  @SuppressWarnings("rawtypes")
  public KafkaOrderMessagingService(@Autowired(required = false) KafkaTemplate kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public void sendOrder(TacoOrder order) {
    if (kafkaTemplate != null) {
      kafkaTemplate.send("tacocloud.orders.topic", order);
    } else {
      log.warn("// Ejercicio 28: KafkaTemplate not available. Cannot send order: {}", order != null ? order.getId() : null);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      if (kafkaTemplate != null) {
        String key = event.getOrderId() != null ? event.getOrderId() : event.getEventId();
        kafkaTemplate.send("tacocloud.orders.topic", key, event);
      } else {
        log.warn("// Ejercicio 28: KafkaTemplate not available. Cannot send order event: {}", event.getEventId());
      }
    }
  }

  public KafkaTemplate getKafkaTemplate() {
    return kafkaTemplate;
  }

  public void setKafkaTemplate(KafkaTemplate kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }
  
}
