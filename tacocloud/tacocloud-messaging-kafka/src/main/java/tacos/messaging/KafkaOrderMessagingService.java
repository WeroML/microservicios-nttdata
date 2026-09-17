package tacos.messaging;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
@Service
public class KafkaOrderMessagingService implements OrderMessagingService {
  
  private KafkaTemplate kafkaTemplate;
  
  @Autowired
  @SuppressWarnings("rawtypes")
  public KafkaOrderMessagingService(KafkaTemplate kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }
  
  @Override
  @SuppressWarnings("unchecked")
  public void sendOrder(TacoOrder order) {
    kafkaTemplate.send("tacocloud.orders.topic", order);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      String key = event.getOrderId() != null ? event.getOrderId() : event.getEventId();
      kafkaTemplate.send("tacocloud.orders.topic", key, event);
    }
  }
  
}
