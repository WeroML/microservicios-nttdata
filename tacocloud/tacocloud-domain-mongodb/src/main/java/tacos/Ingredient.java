package tacos;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access=AccessLevel.PRIVATE, force=true)
@Document
public class Ingredient {

  @Id
  private String id;
  private String name;
  private Type type;

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  private BigDecimal price;
  private Boolean available;
  private Integer stock;

  public enum Type {
    WRAP, PROTEIN, VEGGIES, CHEESE, SAUCE
  }

  public Ingredient(String id, String name, Type type) {
    this(id, name, type, BigDecimal.ZERO, true, 0);
  }

  public Ingredient(String id, String name, Type type, BigDecimal price, Boolean available, Integer stock) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.price = price != null ? price : BigDecimal.ZERO;
    this.available = available != null ? available : true;
    this.stock = stock != null ? stock : 0;
  }

  public Ingredient(String id, String name, Type type, double price, boolean available, int stock) {
    this(id, name, type, BigDecimal.valueOf(price), available, stock);
  }

  public boolean isAvailable() {
    return Boolean.TRUE.equals(this.available) && (this.stock == null || this.stock > 0);
  }

  public boolean isInStock() {
    return this.stock != null && this.stock > 0;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }
}
