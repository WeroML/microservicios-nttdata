package tacos.web.api.dto;

import java.util.Date;

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
public class OrderEtaResponse {

  private String orderId;
  private OrderStatus status;
  private int queuePosition;
  private int ordersAhead;
  private String claimedBy;
  private Integer estimatedPrepMinutes;
  private Integer remainingMinutes;
  private Date estimatedReadyAt;
  private Date placedAt;
}
