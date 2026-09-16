package tacos;

import java.util.Date;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.rest.core.annotation.RestResource;

import lombok.Data;

@Data
@RestResource(rel = "tacos", path = "tacos")
@Document
public class Taco {

  @Id
  private String id;
  
  @NotNull
  @Size(min = 5, message = "Name must be at least 5 characters long")
  private String name;
  
  private Date createdAt = new Date();
  
  @Size(min=1, message="You must choose at least 1 ingredient")
  private List<Ingredient> ingredients;

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  private java.math.BigDecimal price;
  private Boolean available = true;
  private Integer stock = 0;

  public boolean isAvailable() {
    return Boolean.TRUE.equals(this.available) && (this.stock == null || this.stock > 0);
  }

  public boolean isInStock() {
    return this.stock != null && this.stock > 0;
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private Integer quantity = 1;

  public Integer getQuantity() {
    return (this.quantity == null || this.quantity <= 0) ? 1 : this.quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = (quantity == null || quantity <= 0) ? 1 : quantity;
  }

  public java.math.BigDecimal calculatePriceFromIngredients() {
    java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
    if (this.ingredients != null) {
      for (Ingredient ing : this.ingredients) {
        if (ing != null && ing.getPrice() != null) {
          sum = sum.add(ing.getPrice());
        }
      }
    }
    this.price = sum;
    return this.price;
  }
}
