package tacos.versioning;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiVersionInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  private String version;
  private String status; // CURRENT, SUPPORTED, DEPRECATED
  private String description;
  private String releaseDate;
  private String sunsetDate;
  private String documentationUrl;
  private String openApiUrl;
  private List<String> supportedFeatures;
}
