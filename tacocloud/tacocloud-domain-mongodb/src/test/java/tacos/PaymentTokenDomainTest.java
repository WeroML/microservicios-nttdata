package tacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

public class PaymentTokenDomainTest {

  @Test
  public void paymentMethod_shouldNotContainSensitivePanOrCvvFields() {
    List<String> fieldNames = Arrays.stream(PaymentMethod.class.getDeclaredFields())
        .map(Field::getName)
        .collect(Collectors.toList());

    // PAN (ccNumber) and CVV (ccCVV) must be completely eliminated from domain entity
    assertThat(fieldNames).doesNotContain("ccNumber", "ccCVV");
    assertThat(fieldNames).contains("paymentToken", "ccExpiration", "last4");
  }

  @Test
  public void tacoOrder_shouldNotContainSensitivePanOrCvvFields() {
    List<String> fieldNames = Arrays.stream(TacoOrder.class.getDeclaredFields())
        .map(Field::getName)
        .collect(Collectors.toList());

    // PAN (ccNumber) and CVV (ccCVV) must be completely eliminated from domain entity
    assertThat(fieldNames).doesNotContain("ccNumber", "ccCVV");
    assertThat(fieldNames).contains("paymentToken", "ccExpiration", "last4");
  }

  @Test
  public void paymentMethod_shouldStoreAndExposeTokenCorrectly() {
    User user = new User("craig", "password", "Craig Walls", "Street", "City", "State", "76227", "123", "craig@habuma.com");
    PaymentMethod paymentMethod = new PaymentMethod(user, "tok_visa_424242", "12/26", "4242");

    assertThat(paymentMethod.getUser()).isEqualTo(user);
    assertThat(paymentMethod.getPaymentToken()).isEqualTo("tok_visa_424242");
    assertThat(paymentMethod.getToken()).isEqualTo("tok_visa_424242");
    assertThat(paymentMethod.getCcExpiration()).isEqualTo("12/26");
    assertThat(paymentMethod.getLast4()).isEqualTo("4242");
  }

  @Test
  public void tacoOrder_shouldStoreAndExposeTokenCorrectly() {
    TacoOrder order = new TacoOrder();
    order.setPaymentToken("tok_mastercard_9876");
    order.setCcExpiration("11/27");
    order.setLast4("9876");

    assertThat(order.getPaymentToken()).isEqualTo("tok_mastercard_9876");
    assertThat(order.getToken()).isEqualTo("tok_mastercard_9876");
    assertThat(order.getCcExpiration()).isEqualTo("11/27");
    assertThat(order.getLast4()).isEqualTo("9876");

    // Test setter alias
    order.setToken("tok_amex_1111");
    assertThat(order.getPaymentToken()).isEqualTo("tok_amex_1111");
  }
}
