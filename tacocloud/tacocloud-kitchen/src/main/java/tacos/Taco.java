package tacos;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class Taco {

  private String name;
  
  private Date createdAt;

  private List<Ingredient> ingredients;

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private Integer quantity = 1;
  private java.math.BigDecimal price;

}
