package tacos.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimOrderRequest {

  private String notes;
}
