package tacos.messaging;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
@Service
public class JmsOrderMessagingService implements OrderMessagingService {

  private JmsTemplate jms;

  @Autowired
  public JmsOrderMessagingService(JmsTemplate jms) {
    this.jms = jms;
  }

  @Override
  public void sendOrder(TacoOrder order) {
    jms.convertAndSend("tacocloud.order.queue", order,
        this::addOrderSource);
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      jms.convertAndSend("tacocloud.order.queue", event, message -> {
        message.setStringProperty("X_EVENT_ID", event.getEventId());
        message.setStringProperty("X_EVENT_TYPE", event.getEventType() != null ? event.getEventType().name() : "UNKNOWN");
        message.setStringProperty("X_EVENT_VERSION", event.getVersion());
        message.setStringProperty("X_ORDER_SOURCE", event.getSource() != null ? event.getSource() : "WEB");
        return message;
      });
    }
  }
  
  private Message addOrderSource(Message message) throws JMSException {
    message.setStringProperty("X_ORDER_SOURCE", "WEB");
    return message;
  }

}
