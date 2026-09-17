package tacos.web.api.dto;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteRequest {

  @NotBlank(message = "El ID del taco es obligatorio")
  private String tacoId;

  /**
   * Campo no confiable:
   * Si un cliente envía este campo, el servidor NUNCA confiará en él.
   * La identidad se resuelve exclusivamente desde el contexto de seguridad autenticado.
   */
  private String userId;

  public FavoriteRequest(String tacoId) {
    this.tacoId = tacoId;
  }
}
