package ousia.entity;

/**
 * Atributos que toda entidade possui.
 */
public enum Stat {
    VIDA_MAXIMA("Vida"),
    ENERGIA_MAXIMA("Energia"),
    FORCA("Forca"),
    DEFESA("Defesa"),
    VELOCIDADE("Velocidade");

    private final String rotulo;

    Stat(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
