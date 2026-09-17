package tacos.web.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 20: Taco del día determinista y comprobable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacoOfTheDayResponse {

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private LocalDate date;

  private TacoResponse taco;
  private BigDecimal originalPrice;
  private BigDecimal specialPrice;
  private BigDecimal discountPercentage;
  private BigDecimal savings;
  private String promotionHeadline;
}
