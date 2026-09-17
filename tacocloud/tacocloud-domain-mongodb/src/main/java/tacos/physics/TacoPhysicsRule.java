package tacos.physics;

import java.util.Objects;
import java.util.function.Predicate;

import tacos.Taco;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
@FunctionalInterface
public interface TacoPhysicsRule {

  PhysicsResult validate(Taco taco);

  default TacoPhysicsRule and(TacoPhysicsRule other) {
    Objects.requireNonNull(other, "The 'other' rule must not be null.");
    return taco -> {
      PhysicsResult firstResult = this.validate(taco);
      if (!firstResult.isValid()) {
        return firstResult;
      }
      return other.validate(taco);
    };
  }

  default TacoPhysicsRule or(TacoPhysicsRule other) {
    Objects.requireNonNull(other, "The 'other' rule must not be null.");
    return taco -> {
      PhysicsResult firstResult = this.validate(taco);
      if (firstResult.isValid()) {
        return firstResult;
      }
      PhysicsResult secondResult = other.validate(taco);
      if (secondResult.isValid()) {
        return secondResult;
      }
      return PhysicsResult.invalid(firstResult.getReason() + " OR " + secondResult.getReason());
    };
  }

  default TacoPhysicsRule negate(String failureReason) {
    return taco -> {
      PhysicsResult res = this.validate(taco);
      return res.isValid()
          ? PhysicsResult.invalid(failureReason)
          : PhysicsResult.valid();
    };
  }

  static TacoPhysicsRule fromPredicate(Predicate<Taco> predicate, String failureReason) {
    Objects.requireNonNull(predicate, "Predicate must not be null.");
    return taco -> predicate.test(taco)
        ? PhysicsResult.valid()
        : PhysicsResult.invalid(failureReason);
  }
}
