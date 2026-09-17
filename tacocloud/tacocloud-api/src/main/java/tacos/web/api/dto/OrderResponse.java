package tacos.web.api.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;

// Ejercicio 25: Flujo de estados de una orden
// Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

  private String id;
  private Date placedAt;
  private OrderStatus status;
  private String deliveryName;
  private String deliveryStreet;
  private String deliveryCity;
  private String deliveryState;
  private String deliveryZip;
  private List<TacoResponse> tacos;
  private BigDecimal subTotal;
  private BigDecimal discount;
  private BigDecimal total;
  private String couponCode;

  public static OrderResponse fromEntity(TacoOrder order) {
    if (order == null) {
      return null;
    }
    List<TacoResponse> tacoResponses = new ArrayList<>();
    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        tacoResponses.add(TacoResponse.fromEntity(taco));
      }
    }

    return OrderResponse.builder()
        .id(order.getId())
        .placedAt(order.getPlacedAt())
        .status(order.getStatus())
        .deliveryName(order.getDeliveryName())
        .deliveryStreet(order.getDeliveryStreet())
        .deliveryCity(order.getDeliveryCity())
        .deliveryState(order.getDeliveryState())
        .deliveryZip(order.getDeliveryZip())
        .tacos(tacoResponses)
        .subTotal(order.getSubTotal())
        .discount(order.getDiscount())
        .total(order.getTotal())
        .couponCode(order.getCouponCode())
        .build();
  }
}
