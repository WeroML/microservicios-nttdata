package tacos.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 27: Contrato único de eventos de orden
@Service
@Slf4j
public class NoOpOrderMessagingService
       implements OrderMessagingService {

  private final List<OrderEvent> eventHistory = new CopyOnWriteArrayList<>();

  @Override
  public void sendOrder(TacoOrder order) {
    log.info("Sending order to kitchen (legacy): {}", order != null ? order.getId() : null);
    if (order != null) {
      sendOrderEvent(OrderEvent.fromOrder(order, OrderEventType.ORDER_CREATED));
    }
  }

  @Override
  public void sendOrderEvent(OrderEvent event) {
    if (event != null) {
      log.info("Publishing canonical order event: [id={}, type={}, orderId={}, version={}]",
          event.getEventId(), event.getEventType(), event.getOrderId(), event.getVersion());
      eventHistory.add(event);
    }
  }

  public OrderEvent getLastEvent() {
    if (eventHistory.isEmpty()) {
      return null;
    }
    return eventHistory.get(eventHistory.size() - 1);
  }

  public List<OrderEvent> getEventHistory() {
    return Collections.unmodifiableList(new ArrayList<>(eventHistory));
  }

  public void clearEvents() {
    eventHistory.clear();
  }

}
