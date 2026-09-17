package tacos.messaging;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 27: Contrato único de eventos de orden
public interface OrderMessagingService {

  void sendOrder(TacoOrder order);

  void sendOrderEvent(OrderEvent event);

  default void sendOrderEvent(TacoOrder order, OrderEventType eventType) {
    sendOrderEvent(OrderEvent.fromOrder(order, eventType));
  }

  default void sendOrderEvent(TacoOrder order, OrderEventType eventType, String source) {
    sendOrderEvent(OrderEvent.fromOrder(order, eventType, source));
  }
}
