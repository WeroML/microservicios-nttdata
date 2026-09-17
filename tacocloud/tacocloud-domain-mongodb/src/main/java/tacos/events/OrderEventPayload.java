package tacos.events;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Taco;
import tacos.TacoOrder;

// Ejercicio 27: Contrato único de eventos de orden
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventPayload implements Serializable {

  private static final long serialVersionUID = 1L;

  private String orderId;
  private String status;
  private Date placedAt;
  private OrderEventCustomer customer;
  private OrderEventDelivery delivery;
  private List<OrderEventTaco> tacos;
  private OrderEventPricing pricing;
  private OrderEventKitchen kitchen;

  public static OrderEventPayload fromOrder(TacoOrder order) {
    if (order == null) {
      return null;
    }

    List<OrderEventTaco> tacoList = new ArrayList<>();
    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        tacoList.add(OrderEventTaco.fromTaco(taco));
      }
    }

    return OrderEventPayload.builder()
        .orderId(order.getId())
        .status(order.getStatus() != null ? order.getStatus().name() : "CONFIRMED")
        .placedAt(order.getPlacedAt())
        .customer(OrderEventCustomer.fromUser(order.getUser()))
        .delivery(OrderEventDelivery.fromOrder(order))
        .tacos(tacoList)
        .pricing(OrderEventPricing.fromOrder(order))
        .kitchen(OrderEventKitchen.fromOrder(order))
        .build();
  }
}
