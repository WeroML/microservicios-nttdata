package tacos.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
public class KafkaOrderMessagingServiceTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void sendOrder_delegatesToKafkaTemplate() {
    KafkaTemplate kafka = mock(KafkaTemplate.class);
    KafkaOrderMessagingService service = new KafkaOrderMessagingService(kafka);

    TacoOrder order = new TacoOrder();
    order.setId("ORDER-KAFKA-1");

    service.sendOrder(order);

    verify(kafka).send(eq("tacocloud.orders.topic"), eq(order));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void sendOrderEvent_delegatesToKafkaTemplate() {
    KafkaTemplate kafka = mock(KafkaTemplate.class);
    KafkaOrderMessagingService service = new KafkaOrderMessagingService(kafka);

    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-KAFKA")
        .eventType(OrderEventType.ORDER_CREATED)
        .orderId("ORDER-1")
        .build();

    service.sendOrderEvent(event);

    verify(kafka).send(eq("tacocloud.orders.topic"), eq("ORDER-1"), eq(event));
  }

  @Test
  public void defensiveFallback_whenKafkaTemplateIsNull_doesNotCrash() {
    KafkaOrderMessagingService service = new KafkaOrderMessagingService(null);
    TacoOrder order = new TacoOrder();
    OrderEvent event = OrderEvent.builder().eventId("EVT-NULL").build();

    assertDoesNotThrow(() -> service.sendOrder(order));
    assertDoesNotThrow(() -> service.sendOrderEvent(event));
  }

}
