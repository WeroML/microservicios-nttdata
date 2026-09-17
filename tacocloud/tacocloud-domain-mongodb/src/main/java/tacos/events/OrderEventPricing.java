package tacos.events;

import java.io.Serializable;
import java.math.BigDecimal;

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
public class OrderEventPricing implements Serializable {

  private static final long serialVersionUID = 1L;

  private BigDecimal subTotal;
  private BigDecimal discount;
  private BigDecimal total;
  private String couponCode;

  public static OrderEventPricing fromOrder(TacoOrder order) {
    if (order == null) {
      return null;
    }
    return OrderEventPricing.builder()
        .subTotal(order.getSubTotal() != null ? order.getSubTotal() : BigDecimal.ZERO)
        .discount(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO)
        .total(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
        .couponCode(order.getCouponCode())
        .build();
  }
}
