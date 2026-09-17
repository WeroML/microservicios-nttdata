package tacos.events;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder;

// Ejercicio 27: Contrato único de eventos de orden
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventDelivery implements Serializable {

  private static final long serialVersionUID = 1L;

  private String deliveryName;
  private String deliveryStreet;
  private String deliveryCity;
  private String deliveryState;
  private String deliveryZip;

  public static OrderEventDelivery fromOrder(TacoOrder order) {
    if (order == null) {
      return null;
    }
    return OrderEventDelivery.builder()
        .deliveryName(order.getDeliveryName())
        .deliveryStreet(order.getDeliveryStreet())
        .deliveryCity(order.getDeliveryCity())
        .deliveryState(order.getDeliveryState())
        .deliveryZip(order.getDeliveryZip())
        .build();
  }
}
