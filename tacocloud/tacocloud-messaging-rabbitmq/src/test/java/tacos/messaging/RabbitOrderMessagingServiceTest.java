package tacos.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
public class RabbitOrderMessagingServiceTest {

  @Test
  public void sendOrder_delegatesToRabbitTemplate() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    RabbitOrderMessagingService service = new RabbitOrderMessagingService(rabbit);

    TacoOrder order = new TacoOrder();
    order.setId("ORDER-RABBIT-1");

    service.sendOrder(order);

    verify(rabbit).convertAndSend(eq("tacocloud.order.queue"), eq(order), any(MessagePostProcessor.class));
  }

  @Test
  public void sendOrderEvent_delegatesToRabbitTemplate() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    RabbitOrderMessagingService service = new RabbitOrderMessagingService(rabbit);

    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-RABBIT")
        .eventType(OrderEventType.ORDER_CREATED)
        .orderId("ORDER-1")
        .build();

    service.sendOrderEvent(event);

    verify(rabbit).convertAndSend(eq("tacocloud.order.queue"), eq(event), any(MessagePostProcessor.class));
  }

  @Test
  public void defensiveFallback_whenRabbitTemplateIsNull_doesNotCrash() {
    RabbitOrderMessagingService service = new RabbitOrderMessagingService(null);
    TacoOrder order = new TacoOrder();
    OrderEvent event = OrderEvent.builder().eventId("EVT-NULL").build();

    assertDoesNotThrow(() -> service.sendOrder(order));
    assertDoesNotThrow(() -> service.sendOrderEvent(event));
  }

}
