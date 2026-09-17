package tacos.web.api.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder.OrderStatus;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenQueueItem {

  private String orderId;
  private Date placedAt;
  private OrderStatus status;
  private int queuePosition;
  private int ordersAhead;
  private String claimedBy;
  private Date claimedAt;
  private int tacoCount;
  private List<String> tacoSummary;
  private Integer estimatedPrepMinutes;
  private Date estimatedReadyAt;
  private Long elapsedMinutes;
}
