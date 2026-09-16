package tacos;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Document
@Data
@NoArgsConstructor(force=true, access=AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class PaymentMethod {

  @Id
  private String id;
  
  private final User user;
  private final String paymentToken;
  private final String ccExpiration;
  private final String last4;

  public PaymentMethod(User user, String paymentToken) {
    this(user, paymentToken, null, null);
  }

  public String getToken() {
    return this.paymentToken;
  }
}
