package tacos.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 24: Reordenar una compra anterior con reglas actuales
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReorderRequest {
  private String deliveryName;
  private String deliveryStreet;
  private String deliveryCity;
  private String deliveryState;
  private String deliveryZip;

  private String paymentToken;
  private String ccExpiration;
  private String last4;

  private String couponCode;
  private Boolean dropExpiredCoupon;
}
