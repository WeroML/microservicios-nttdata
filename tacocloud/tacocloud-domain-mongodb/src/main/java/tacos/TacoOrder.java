package tacos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document
public class TacoOrder implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  private Date placedAt = new Date();

  private User user;

  private String deliveryName;

  private String deliveryStreet;

  private String deliveryCity;

  private String deliveryState;

  private String deliveryZip;

  // PCI-DSS: Tokenizar pago y eliminar PAN/CVV del dominio
  private String paymentToken;

  private String ccExpiration;

  private String last4;


  private List<Taco> tacos = new ArrayList<>();

  public void addTaco(Taco design) {
    this.tacos.add(design);
  }

  public String getToken() {
    return this.paymentToken;
  }

  public void setToken(String token) {
    this.paymentToken = token;
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private java.math.BigDecimal total = java.math.BigDecimal.ZERO;

  public java.math.BigDecimal calculateTotal() {
    java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
    if (this.tacos != null) {
      for (Taco taco : this.tacos) {
        if (taco != null && taco.getPrice() != null) {
          int qty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
          sum = sum.add(taco.getPrice().multiply(java.math.BigDecimal.valueOf(qty)));
        }
      }
    }
    this.total = sum;
    return this.total;
  }

}
