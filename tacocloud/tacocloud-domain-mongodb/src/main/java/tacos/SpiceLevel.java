package tacos;

// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
public enum SpiceLevel {
  NONE(0, "No picante"),
  MILD(1, "Poco picante"),
  MEDIUM(2, "Medio"),
  HOT(3, "Picante"),
  EXTRA_HOT(4, "Muy picante");

  private final int level;
  private final String displayName;

  SpiceLevel(int level, String displayName) {
    this.level = level;
    this.displayName = displayName;
  }

  public int getLevel() {
    return level;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isSpicy() {
    return this.level > 0;
  }

  public static SpiceLevel max(SpiceLevel a, SpiceLevel b) {
    if (a == null) return b != null ? b : NONE;
    if (b == null) return a;
    return a.getLevel() >= b.getLevel() ? a : b;
  }
}
