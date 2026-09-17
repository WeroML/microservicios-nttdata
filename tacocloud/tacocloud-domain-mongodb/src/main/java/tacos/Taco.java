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

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  private java.util.Set<DietaryLabel> dietaryLabels = new java.util.HashSet<>();
  private java.util.Set<Allergen> allergens = new java.util.HashSet<>();
  private SpiceLevel spiceLevel = SpiceLevel.NONE;

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

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  public java.util.Set<Allergen> computeAllergens() {
    java.util.Set<Allergen> result = new java.util.HashSet<>();
    if (this.allergens != null) {
      result.addAll(this.allergens);
    }
    if (this.ingredients != null) {
      for (Ingredient ing : this.ingredients) {
        if (ing != null && ing.getAllergens() != null) {
          result.addAll(ing.getAllergens());
        }
      }
    }
    return result;
  }

  public SpiceLevel computeSpiceLevel() {
    SpiceLevel max = (this.spiceLevel != null) ? this.spiceLevel : SpiceLevel.NONE;
    if (this.ingredients != null) {
      for (Ingredient ing : this.ingredients) {
        if (ing != null && ing.getSpiceLevel() != null) {
          max = SpiceLevel.max(max, ing.getSpiceLevel());
        }
      }
    }
    return max;
  }

  public java.util.Set<DietaryLabel> computeDietaryLabels() {
    if (this.ingredients == null || this.ingredients.isEmpty()) {
      return this.dietaryLabels != null ? new java.util.HashSet<>(this.dietaryLabels) : new java.util.HashSet<>();
    }

    java.util.Set<DietaryLabel> result = new java.util.HashSet<>();
    for (DietaryLabel label : DietaryLabel.values()) {
      boolean allSatisfy = true;
      for (Ingredient ing : this.ingredients) {
        if (ing == null) continue;
        if (label == DietaryLabel.GLUTEN_FREE) {
          if (ing.hasAllergen(Allergen.GLUTEN) || !ing.hasDietaryLabel(DietaryLabel.GLUTEN_FREE)) {
            allSatisfy = false;
            break;
          }
        } else if (label == DietaryLabel.DAIRY_FREE) {
          if (ing.hasAllergen(Allergen.DAIRY) || !ing.hasDietaryLabel(DietaryLabel.DAIRY_FREE)) {
            allSatisfy = false;
            break;
          }
        } else {
          if (!ing.hasDietaryLabel(label)) {
            allSatisfy = false;
            break;
          }
        }
      }
      if (allSatisfy) {
        result.add(label);
      }
    }
    if (this.dietaryLabels != null) {
      result.addAll(this.dietaryLabels);
    }
    return result;
  }

  public void updateDietaryAndAllergenInfo() {
    this.allergens = computeAllergens();
    this.spiceLevel = computeSpiceLevel();
    this.dietaryLabels = computeDietaryLabels();
  }

  public boolean hasAllergen(Allergen allergen) {
    return computeAllergens().contains(allergen);
  }

  public boolean hasDietaryLabel(DietaryLabel label) {
    return computeDietaryLabels().contains(label);
  }

  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  public tacos.physics.PhysicsResult validatePhysics() {
    return validatePhysics(tacos.physics.TacoPhysicsRules.standard());
  }

  public tacos.physics.PhysicsResult validatePhysics(tacos.physics.TacoPhysicsRule rule) {
    if (rule == null) {
      return tacos.physics.PhysicsResult.valid();
    }
    return rule.validate(this);
  }
}
