package ousia.progression;

import java.util.ArrayList;
import java.util.List;

/**
 * Controla nivel e XP.
 */
public final class Progression {

    private final ExperienceCurve curva;
    private final int nivelMaximo;
    private int nivel;
    private long xpAtual;
    private long xpTotal;

    public Progression(ExperienceCurve curva, int nivelMaximo, int nivelInicial) {
        if (nivelInicial < 1 || nivelInicial > nivelMaximo) {
            throw new IllegalArgumentException("nivel inicial invalido");
        }
        this.curva = curva;
        this.nivelMaximo = nivelMaximo;
        this.nivel = nivelInicial;
    }

    /**
     * Adiciona XP e sobe quantos niveis forem necessarios.
     *
     * @return lista dos novos niveis alcançados
     */
    public List<Integer> addXp(long quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("XP nao pode ser negativo");
        }
        List<Integer> alcancados = new ArrayList<Integer>();
        if (isNivelMaximo()) {
            return alcancados;
        }
        xpTotal += quantidade;
        xpAtual += quantidade;
        while (!isNivelMaximo() && xpAtual >= xpParaProximoNivel()) {
            xpAtual -= xpParaProximoNivel();
            nivel++;
            alcancados.add(nivel);
        }
        if (isNivelMaximo()) {
            xpAtual = 0;
        }
        return alcancados;
    }

    public int getNivel() {
        return nivel;
    }

    public int getNivelMaximo() {
        return nivelMaximo;
    }

    public boolean isNivelMaximo() {
        return nivel >= nivelMaximo;
    }

    public long getXpAtual() {
        return xpAtual;
    }

    public long getXpTotal() {
        return xpTotal;
    }

    public long xpParaProximoNivel() {
        return isNivelMaximo() ? 0 : curva.xpParaProximoNivel(nivel);
    }

    public double progressoDoNivel() {
        if (isNivelMaximo()) {
            return 1;
        }
        return (double) xpAtual / xpParaProximoNivel();
    }
}
