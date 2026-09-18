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
  
  // Ejercicio 31: Correlation ID de HTTP a evento y logs
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
              if (order != null && order.getCorrelationId() != null) {
                props.setHeader("X_CORRELATION_ID", order.getCorrelationId());
                props.setCorrelationId(order.getCorrelationId());
                log.info("// Ejercicio 31: [RABBITMQ] Enviando orden {} con correlationId={}", order.getId(), order.getCorrelationId());
              }
              return message;
            } 
          });
    } else {
      log.warn("// Ejercicio 28: RabbitTemplate not available. Cannot send order: {}", order != null ? order.getId() : null);
    }
  }

  // Ejercicio 31: Correlation ID de HTTP a evento y logs
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
          if (event.getCorrelationId() != null) {
            props.setHeader("X_CORRELATION_ID", event.getCorrelationId());
            props.setCorrelationId(event.getCorrelationId());
            log.info("// Ejercicio 31: [RABBITMQ] Enviando evento {} con correlationId={}", event.getEventId(), event.getCorrelationId());
          }
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
