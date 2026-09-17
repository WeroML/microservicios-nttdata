package tacos.web.api.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Allergen;
import tacos.DietaryLabel;
import tacos.SpiceLevel;

// Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacoSearchCriteria {

  private String search;
  private BigDecimal minPrice;
  private BigDecimal maxPrice;
  private Boolean available;
  private Boolean inStock;
  private DietaryLabel dietary;
  private Allergen excludeAllergen;
  private SpiceLevel maxSpice;
  private String ingredient;

  @Builder.Default
  private String sortBy = "name";

  @Builder.Default
  private String sortDir = "asc";

  @Builder.Default
  private int page = 0;

  @Builder.Default
  private int size = 10;
}
