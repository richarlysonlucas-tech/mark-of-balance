package ousia.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ficha de atributos: valores base e modificadores.
 * Valor final = (base + FLAT) * (1 + PERCENT).
 */
public final class StatSheet {

    private final Map<Stat, Double> base = new EnumMap<Stat, Double>(Stat.class);
    private final List<StatModifier> modifiers =
            new ArrayList<StatModifier>();

    public StatSheet() {
        for (Stat s : Stat.values()) {
            base.put(s, 0.0);
        }
    }

    public StatSheet setBase(Stat stat, double value) {
        base.put(stat, value);
        return this;
    }

    public void addBase(Stat stat, double delta) {
        base.put(stat, base.get(stat) + delta);
    }

    public double getBase(Stat stat) {
        return base.get(stat);
    }

    public void addModifier(StatModifier modifier) {
        modifiers.add(modifier);
    }

    /** Remove todos os modificadores de uma origem. */
    public boolean removeModifiersFrom(String source) {
        boolean removed = false;
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            if (modifiers.get(i).getSource().equals(source)) {
                modifiers.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    public double get(Stat stat) {
        double flat = 0;
        double percent = 0;
        for (StatModifier m : modifiers) {
            if (m.getStat() != stat) {
                continue;
            }
            if (m.getType() == StatModifier.Type.FLAT) {
                flat += m.getValue();
            } else {
                percent += m.getValue();
            }
        }
        return Math.max(0, (base.get(stat) + flat) * (1 + percent));
    }

    public int getInt(Stat stat) {
        return (int) Math.round(get(stat));
    }
}
