package tacos.physics;

import tacos.physics.rules.BaseFoundationRule;
import tacos.physics.rules.MaxCapacityRule;
import tacos.physics.rules.MoistureBalanceRule;
import tacos.physics.rules.SolidFillingRule;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public final class TacoPhysicsRules {

  private TacoPhysicsRules() {}

  /**
   * Conjunto estándar de reglas de física del taco:
   * - Al menos 1 base WRAP y máximo 2 WRAPs.
   * - Máximo 8 ingredientes de capacidad total.
   * - Máximo 2 salsas (balance de humedad).
   */
  public static TacoPhysicsRule standard() {
    return new BaseFoundationRule()
        .and(new MaxCapacityRule(8))
        .and(new MoistureBalanceRule(2));
  }

  /**
   * Conjunto estricto de reglas de física del taco:
   * - Cumple estándar y además exige al menos un relleno sólido.
   */
  public static TacoPhysicsRule strict() {
    return standard().and(new SolidFillingRule());
  }

  /**
   * Conjunto permisivo de reglas:
   * - Permite hasta 12 ingredientes y hasta 3 salsas.
   */
  public static TacoPhysicsRule permissive() {
    return new BaseFoundationRule(1, 3)
        .and(new MaxCapacityRule(12))
        .and(new MoistureBalanceRule(3));
  }
}
