package tacos.physics;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class PhysicsResult implements Serializable {

  private final boolean valid;
  private final String reason;
  private final List<String> violations;

  private static final PhysicsResult VALID_INSTANCE = new PhysicsResult(true, "Taco complies with all laws of taco physics.", Collections.emptyList());

  public PhysicsResult(boolean valid, String reason, List<String> violations) {
    this.valid = valid;
    this.reason = reason;
    this.violations = violations != null ? Collections.unmodifiableList(violations) : Collections.emptyList();
  }

  public static PhysicsResult valid() {
    return VALID_INSTANCE;
  }

  public static PhysicsResult invalid(String reason) {
    return new PhysicsResult(false, reason, Collections.singletonList(reason));
  }

  public static PhysicsResult invalid(String reason, List<String> violations) {
    return new PhysicsResult(false, reason, violations);
  }

  public boolean isValid() {
    return valid;
  }

  public String getReason() {
    return reason;
  }

  public List<String> getViolations() {
    return violations;
  }

  @Override
  public String toString() {
    return valid ? "PhysicsResult{VALID}" : "PhysicsResult{INVALID: " + reason + "}";
  }
}
