package tacos.messaging;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Service("jmsOrderMessagingService")
@Slf4j
public class JmsOrderMessagingService implements OrderMessagingService {

  private JmsTemplate jms;

  public JmsOrderMessagingService() {
    this(null);
  }

  @Autowired
  public JmsOrderMessagingService(@Autowired(required = false) JmsTemplate jms) {
    this.jms = jms;
  }

  @Override
  public void sendOrder(TacoOrder order) {
    if (jms != null) {
      jms.convertAndSend("tacocloud.order.queue", order,
          this::addOrderSource);
    } else {
      log.warn("// Ejercicio 28: JmsTemplate not available. Cannot send order: {}", order != null ? order.getId() : null);
    }
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      if (jms != null) {
        jms.convertAndSend("tacocloud.order.queue", event, message -> {
          message.setStringProperty("X_EVENT_ID", event.getEventId());
          message.setStringProperty("X_EVENT_TYPE", event.getEventType() != null ? event.getEventType().name() : "UNKNOWN");
          message.setStringProperty("X_EVENT_VERSION", event.getVersion());
          message.setStringProperty("X_ORDER_SOURCE", event.getSource() != null ? event.getSource() : "WEB");
          return message;
        });
      } else {
        log.warn("// Ejercicio 28: JmsTemplate not available. Cannot send order event: {}", event.getEventId());
      }
    }
  }
  
  private Message addOrderSource(Message message) throws JMSException {
    message.setStringProperty("X_ORDER_SOURCE", "WEB");
    return message;
  }

  public JmsTemplate getJmsTemplate() {
    return jms;
  }

  public void setJmsTemplate(JmsTemplate jms) {
    this.jms = jms;
  }

}
