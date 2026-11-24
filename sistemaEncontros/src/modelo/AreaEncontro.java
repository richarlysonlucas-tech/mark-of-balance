package modelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zona da fase onde encontros aleatórios podem acontecer.
 */
public class AreaEncontro {
    private final String id;
    private final double xInicial;
    private final double yInicial;
    private final double largura;
    private final double altura;
    private final int chanceEncontroPercentual;
    private final List<Inimigo> inimigosPossiveis;

    public AreaEncontro(String id, double xInicial, double yInicial, double largura, double altura,
            int chanceEncontroPercentual, List<Inimigo> inimigosPossiveis) {
        this.id = validarTexto(id);
        if (!Double.isFinite(xInicial) || !Double.isFinite(yInicial)
                || !Double.isFinite(largura) || !Double.isFinite(altura)
                || largura < 0 || altura < 0) {
            throw new IllegalArgumentException("As coordenadas e dimensões da área devem ser finitas e não negativas.");
        }
        if (chanceEncontroPercentual < 0 || chanceEncontroPercentual > 100) {
            throw new IllegalArgumentException("A chance de encontro deve estar entre 0 e 100.");
        }
        Objects.requireNonNull(inimigosPossiveis, "A lista de inimigos possíveis é obrigatória.");
        if (inimigosPossiveis.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A lista de inimigos não pode conter valores nulos.");
        }
        this.xInicial = xInicial;
        this.yInicial = yInicial;
        this.largura = largura;
        this.altura = altura;
        this.chanceEncontroPercentual = chanceEncontroPercentual;

        // Uma cópia evita que a área seja alterada sem querer por outra classe.
        this.inimigosPossiveis = new ArrayList<Inimigo>(inimigosPossiveis);
    }

    private static String validarTexto(String texto) {
        Objects.requireNonNull(texto, "O id da área é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O id da área não pode estar vazio.");
        }
        return texto.trim();
    }

    public String getId() {
        return id;
    }

    public double getXInicial() {
        return xInicial;
    }

    public double getYInicial() {
        return yInicial;
    }

    public double getLargura() {
        return largura;
    }

    public double getAltura() {
        return altura;
    }

    public int getChanceEncontroPercentual() {
        return chanceEncontroPercentual;
    }

    public List<Inimigo> getInimigosPossiveis() {
        return new ArrayList<Inimigo>(inimigosPossiveis);
    }
}
