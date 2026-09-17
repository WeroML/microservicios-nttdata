package tacos.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
public class JmsOrderMessagingServiceTest {

  @Test
  public void sendOrder_delegatesToJmsTemplate() {
    JmsTemplate jms = mock(JmsTemplate.class);
    JmsOrderMessagingService service = new JmsOrderMessagingService(jms);

    TacoOrder order = new TacoOrder();
    order.setId("ORDER-JMS-1");

    service.sendOrder(order);

    verify(jms).convertAndSend(eq("tacocloud.order.queue"), eq(order), any(MessagePostProcessor.class));
  }

  @Test
  public void sendOrderEvent_delegatesToJmsTemplate() {
    JmsTemplate jms = mock(JmsTemplate.class);
    JmsOrderMessagingService service = new JmsOrderMessagingService(jms);

    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-JMS")
        .eventType(OrderEventType.ORDER_CREATED)
        .orderId("ORDER-1")
        .build();

    service.sendOrderEvent(event);

    verify(jms).convertAndSend(eq("tacocloud.order.queue"), eq(event), any(MessagePostProcessor.class));
  }

  @Test
  public void defensiveFallback_whenJmsTemplateIsNull_doesNotCrash() {
    JmsOrderMessagingService service = new JmsOrderMessagingService(null);
    TacoOrder order = new TacoOrder();
    OrderEvent event = OrderEvent.builder().eventId("EVT-NULL").build();

    assertDoesNotThrow(() -> service.sendOrder(order));
    assertDoesNotThrow(() -> service.sendOrderEvent(event));
  }

}
