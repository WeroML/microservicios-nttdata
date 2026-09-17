package tacos.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectBrokerRequest {

  private String broker;

}
