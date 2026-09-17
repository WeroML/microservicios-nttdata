package tacos.messaging;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.events.OrderEvent;

// Ejercicio 27: Contrato único de eventos de orden
// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Service("rabbitOrderMessagingService")
@Slf4j
public class RabbitOrderMessagingService
       implements OrderMessagingService {
  
  private RabbitTemplate rabbit;

  public RabbitOrderMessagingService() {
    this(null);
  }
  
  @Autowired
  public RabbitOrderMessagingService(@Autowired(required = false) RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }
  
  @Override
  public void sendOrder(TacoOrder order) {
    if (rabbit != null) {
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
    } else {
      log.warn("// Ejercicio 28: RabbitTemplate not available. Cannot send order: {}", order != null ? order.getId() : null);
    }
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      if (rabbit != null) {
        rabbit.convertAndSend("tacocloud.order.queue", event, message -> {
          MessageProperties props = message.getMessageProperties();
          props.setHeader("X_EVENT_ID", event.getEventId());
          props.setHeader("X_EVENT_TYPE", event.getEventType() != null ? event.getEventType().name() : "UNKNOWN");
          props.setHeader("X_EVENT_VERSION", event.getVersion());
          props.setHeader("X_ORDER_SOURCE", event.getSource() != null ? event.getSource() : "WEB");
          return message;
        });
      } else {
        log.warn("// Ejercicio 28: RabbitTemplate not available. Cannot send order event: {}", event.getEventId());
      }
    }
  }

  public RabbitTemplate getRabbitTemplate() {
    return rabbit;
  }

  public void setRabbitTemplate(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }
  
}
