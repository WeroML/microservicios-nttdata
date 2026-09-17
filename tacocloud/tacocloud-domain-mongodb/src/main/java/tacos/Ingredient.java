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

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  private java.util.Set<DietaryLabel> dietaryLabels = new java.util.HashSet<>();
  private java.util.Set<Allergen> allergens = new java.util.HashSet<>();
  private SpiceLevel spiceLevel = SpiceLevel.NONE;

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

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  public Ingredient(String id, String name, Type type, BigDecimal price, Boolean available, Integer stock,
                    java.util.Set<DietaryLabel> dietaryLabels, java.util.Set<Allergen> allergens, SpiceLevel spiceLevel) {
    this(id, name, type, price, available, stock);
    if (dietaryLabels != null) {
      this.dietaryLabels = new java.util.HashSet<>(dietaryLabels);
    }
    if (allergens != null) {
      this.allergens = new java.util.HashSet<>(allergens);
    }
    this.spiceLevel = spiceLevel != null ? spiceLevel : SpiceLevel.NONE;
  }

  public boolean hasDietaryLabel(DietaryLabel label) {
    return this.dietaryLabels != null && this.dietaryLabels.contains(label);
  }

  public boolean hasAllergen(Allergen allergen) {
    return this.allergens != null && this.allergens.contains(allergen);
  }

  public boolean containsAnyAllergen(java.util.Collection<Allergen> targetAllergens) {
    if (this.allergens == null || targetAllergens == null) {
      return false;
    }
    for (Allergen a : targetAllergens) {
      if (this.allergens.contains(a)) {
        return true;
      }
    }
    return false;
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

  // Ejercicio 16: Reservar y liberar inventario sin vender aire
  public boolean hasSufficientStock(int requiredQty) {
    if (this.stock == null) {
      return true;
    }
    return isAvailable() && this.stock >= requiredQty;
  }

  public void decrementStock(int quantity) {
    if (this.stock != null) {
      this.stock = Math.max(0, this.stock - quantity);
      if (this.stock == 0) {
        this.available = false;
      }
    }
  }

  public void incrementStock(int quantity) {
    if (this.stock != null) {
      this.stock += quantity;
      if (this.stock > 0) {
        this.available = true;
      }
    }
  }
}
