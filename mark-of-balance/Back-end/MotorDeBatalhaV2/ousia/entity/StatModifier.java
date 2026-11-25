package ousia.entity;

/**
 * Bonus ou penalidade aplicado sobre um atributo.
 */
public final class StatModifier {

    public enum Type {
        FLAT,
        PERCENT
    }

    private final String source;
    private final Stat stat;
    private final Type type;
    private final double value;

    private StatModifier(String source, Stat stat, Type type, double value) {
        if (source == null || stat == null || type == null) {
            throw new IllegalArgumentException(
                    "source, stat e type sao obrigatorios");
        }
        this.source = source;
        this.stat = stat;
        this.type = type;
        this.value = value;
    }

    public static StatModifier flat(String source, Stat stat, double value) {
        return new StatModifier(source, stat, Type.FLAT, value);
    }

    public static StatModifier percent(String source, Stat stat, double value) {
        return new StatModifier(source, stat, Type.PERCENT, value);
    }

    public String getSource() {
        return source;
    }

    public Stat getStat() {
        return stat;
    }

    public Type getType() {
        return type;
    }

    public double getValue() {
        return value;
    }
}
