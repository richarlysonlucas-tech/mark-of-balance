package modelo;

import java.util.Objects;

/**
 * Entidade visível no mundo, usada para verificar proximidade.
 */
public class EntidadeMundo {
    private final String id;
    private final String areaId;
    private final double posicaoX;
    private final double posicaoY;
    private final double raioDeteccao;
    private final Inimigo inimigo;

    public EntidadeMundo(String id, String areaId, double posicaoX, double posicaoY,
            double raioDeteccao, Inimigo inimigo) {
        this.id = validarTexto(id, "id");
        this.areaId = validarTexto(areaId, "areaId");
        if (!Double.isFinite(posicaoX) || !Double.isFinite(posicaoY)
                || !Double.isFinite(raioDeteccao) || raioDeteccao < 0) {
            throw new IllegalArgumentException("Posição e raio da entidade devem ser finitos e o raio não pode ser negativo.");
        }
        this.posicaoX = posicaoX;
        this.posicaoY = posicaoY;
        this.raioDeteccao = raioDeteccao;
        this.inimigo = Objects.requireNonNull(inimigo, "A entidade deve possuir um inimigo.");
    }

    private static String validarTexto(String texto, String campo) {
        Objects.requireNonNull(texto, "O " + campo + " da entidade é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O " + campo + " da entidade não pode estar vazio.");
        }
        return texto.trim();
    }

    public String getId() {
        return id;
    }

    public String getAreaId() {
        return areaId;
    }

    public boolean pertenceA(String outraAreaId) {
        return areaId.equals(outraAreaId);
    }

    public double getPosicaoX() {
        return posicaoX;
    }

    public double getPosicaoY() {
        return posicaoY;
    }

    public double getRaioDeteccao() {
        return raioDeteccao;
    }

    public Inimigo getInimigo() {
        return inimigo;
    }
}
