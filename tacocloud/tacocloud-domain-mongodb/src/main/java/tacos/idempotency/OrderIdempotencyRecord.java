package tacos.idempotency;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoOrder;

// Ejercicio 34: Idempotency-Key en creación de órdenes
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "order_idempotency_records")
public class OrderIdempotencyRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  @Indexed(unique = true)
  private String key;

  private String userId;
  private String requestHash;
  private String orderId;

  @Builder.Default
  private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

  private TacoOrder savedOrder;

  @Builder.Default
  private Date createdAt = new Date();

  private Date completedAt;
  private Date expiresAt;

  /**
   * Determina si el registro ha expirado temporalmente.
   */
  public boolean isExpired() {
    return expiresAt != null && new Date().after(expiresAt);
  }
}
