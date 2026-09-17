package tacos.events;

import java.io.Serializable;
import java.util.Date;

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
public class OrderEventKitchen implements Serializable {

  private static final long serialVersionUID = 1L;

  private String claimedBy;
  private Date claimedAt;
  private Integer estimatedPrepMinutes;
  private Date estimatedReadyAt;

  public static OrderEventKitchen fromOrder(TacoOrder order) {
    if (order == null) {
      return null;
    }
    return OrderEventKitchen.builder()
        .claimedBy(order.getClaimedBy())
        .claimedAt(order.getClaimedAt())
        .estimatedPrepMinutes(order.getEstimatedPrepMinutes())
        .estimatedReadyAt(order.getEstimatedReadyAt())
        .build();
  }
}
