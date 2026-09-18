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
  
  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  @Override
  @SuppressWarnings("unchecked")
  public void sendOrder(TacoOrder order) {
    if (kafkaTemplate != null) {
      if (order != null && order.getCorrelationId() != null) {
        org.apache.kafka.clients.producer.ProducerRecord<String, TacoOrder> record =
            new org.apache.kafka.clients.producer.ProducerRecord<>("tacocloud.orders.topic", order.getId(), order);
        record.headers().add("X_CORRELATION_ID", order.getCorrelationId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        log.info("// Ejercicio 31: [KAFKA] Enviando orden {} con correlationId={}", order.getId(), order.getCorrelationId());
        kafkaTemplate.send(record);
      } else {
        kafkaTemplate.send("tacocloud.orders.topic", order);
      }
    } else {
      log.warn("// Ejercicio 28: KafkaTemplate not available. Cannot send order: {}", order != null ? order.getId() : null);
    }
  }

  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  @Override
  @SuppressWarnings("unchecked")
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      if (kafkaTemplate != null) {
        String key = event.getOrderId() != null ? event.getOrderId() : event.getEventId();
        if (event.getCorrelationId() != null) {
          org.apache.kafka.clients.producer.ProducerRecord<String, OrderEvent> record =
              new org.apache.kafka.clients.producer.ProducerRecord<>("tacocloud.orders.topic", key, event);
          record.headers().add("X_CORRELATION_ID", event.getCorrelationId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          log.info("// Ejercicio 31: [KAFKA] Enviando evento {} con correlationId={}", event.getEventId(), event.getCorrelationId());
          kafkaTemplate.send(record);
        } else {
          kafkaTemplate.send("tacocloud.orders.topic", key, event);
        }
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
