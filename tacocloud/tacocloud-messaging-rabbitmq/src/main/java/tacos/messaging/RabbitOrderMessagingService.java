package tacos.messaging;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
@Service
public class RabbitOrderMessagingService
       implements OrderMessagingService {
  
  private RabbitTemplate rabbit;
  
  @Autowired
  public RabbitOrderMessagingService(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }
  
  @Override
  public void sendOrder(TacoOrder order) {
    rabbit.convertAndSend("tacocloud.order.queue", order,
        new MessagePostProcessor() {
          @Override
          public Message postProcessMessage(Message message)
              throws AmqpException {
            MessageProperties props = message.getMessageProperties();
            props.setHeader("X_ORDER_SOURCE", "WEB");
            return message;
          } 
        });
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      rabbit.convertAndSend("tacocloud.order.queue", event, message -> {
        MessageProperties props = message.getMessageProperties();
        props.setHeader("X_EVENT_ID", event.getEventId());
        props.setHeader("X_EVENT_TYPE", event.getEventType() != null ? event.getEventType().name() : "UNKNOWN");
        props.setHeader("X_EVENT_VERSION", event.getVersion());
        props.setHeader("X_ORDER_SOURCE", event.getSource() != null ? event.getSource() : "WEB");
        return message;
      });
    }
  }
  
}
