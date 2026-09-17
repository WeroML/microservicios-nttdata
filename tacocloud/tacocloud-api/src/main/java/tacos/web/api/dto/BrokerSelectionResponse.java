package tacos.web.api.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerSelectionResponse {

  private String activeBroker;
  private Set<String> availableBrokers;
  private String message;

}
