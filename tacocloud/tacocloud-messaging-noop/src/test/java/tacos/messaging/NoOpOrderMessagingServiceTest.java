package tacos.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
public class NoOpOrderMessagingServiceTest {

  private NoOpOrderMessagingService service;

  @BeforeEach
  public void setUp() {
    service = new NoOpOrderMessagingService();
  }

  @Test
  public void sendOrder_recordsOrderCreatedEvent() {
    TacoOrder order = new TacoOrder();
    order.setId("ORDER-123");
    order.setDeliveryName("Tester");

    service.sendOrder(order);

    assertEquals(1, service.getEventHistory().size());
    OrderEvent event = service.getLastEvent();
    assertNotNull(event);
    assertEquals("ORDER-123", event.getOrderId());
    assertEquals(OrderEventType.ORDER_CREATED, event.getEventType());
  }

  @Test
  public void sendOrderEvent_recordsDirectEvent() {
    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-1")
        .eventType(OrderEventType.ORDER_CONFIRMED)
        .orderId("ORDER-456")
        .version("1.0.0")
        .build();

    service.sendOrderEvent(event);

    assertEquals(1, service.getEventHistory().size());
    assertEquals(event, service.getLastEvent());

    service.clearEvents();
    assertEquals(0, service.getEventHistory().size());
    assertNull(service.getLastEvent());
  }

}
