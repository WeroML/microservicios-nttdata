package tacos.web.api.dto;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder.OrderStatus;

// Ejercicio 25: Flujo de estados de una orden
// Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
// Ejercicio 9: Validación y errores tipo Problem Details
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

  @NotNull(message = "El nuevo estado de la orden es obligatorio")
  private OrderStatus status;

  private String reason;
}
