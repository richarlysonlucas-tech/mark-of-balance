package ousia.progression;

/**
 * Define quanto XP e preciso para avancar ao proximo nivel.
 */
@FunctionalInterface
public interface ExperienceCurve {

    long xpParaProximoNivel(int nivelAtual);

    /** Curva polinomial: base * nivel ^ expoente. */
    static ExperienceCurve polinomial(final double base, final double expoente) {
        return new ExperienceCurve() {
            @Override
            public long xpParaProximoNivel(int nivelAtual) {
                return Math.round(base * Math.pow(nivelAtual, expoente));
            }
        };
    }
}
