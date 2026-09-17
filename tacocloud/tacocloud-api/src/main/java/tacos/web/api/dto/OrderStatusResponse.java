package tacos.web.api.dto;

import java.util.Date;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder.OrderStatus;

// Ejercicio 25: Flujo de estados de una orden
// Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusResponse {

  private String orderId;
  private OrderStatus previousStatus;
  private OrderStatus currentStatus;
  private String reason;
  private Date updatedAt;
  private Set<OrderStatus> allowedNextStates;
  private boolean terminal;
}
