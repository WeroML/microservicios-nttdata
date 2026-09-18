package tacos;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Data
public class TacoOrder {

  private String id;
  private Date placedAt;
  private String deliveryName;
  private String deliveryStreet;
  private String deliveryCity;
  private String deliveryState;
  private String deliveryZip;

  private List<Taco> tacos = new ArrayList<>();

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private java.math.BigDecimal total = java.math.BigDecimal.ZERO;

}
