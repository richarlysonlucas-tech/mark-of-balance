package modelo;

import java.util.Objects;

/**
 * Representa apenas os dados de posição usados pelo sistema de encontros.
 */
public class Jogador {
    private final String id;
    private final String areaAtualId;
    private double posicaoX;
    private double posicaoY;

    public Jogador(String id, double posicaoX, double posicaoY, String areaAtualId) {
        this.id = validarTexto(id, "id");
        this.areaAtualId = validarTexto(areaAtualId, "areaAtualId");
        validarPosicao(posicaoX, posicaoY);
        this.posicaoX = posicaoX;
        this.posicaoY = posicaoY;
    }

    public String getId() {
        return id;
    }

    public double getPosicaoX() {
        return posicaoX;
    }

    public double getPosicaoY() {
        return posicaoY;
    }

    public String getAreaAtualId() {
        return areaAtualId;
    }

    public void atualizarPosicao(double posicaoX, double posicaoY, String areaAtualId) {
        validarPosicao(posicaoX, posicaoY);
        String novaAreaId = validarTexto(areaAtualId, "areaAtualId");
        if (!this.areaAtualId.equals(novaAreaId)) {
            throw new IllegalArgumentException(
                "A área atual não pode ser alterada por atualizarPosicao; crie uma nova instância de Jogador."
            );
        }
        this.posicaoX = posicaoX;
        this.posicaoY = posicaoY;
    }

    private static String validarTexto(String texto, String campo) {
        Objects.requireNonNull(texto, "O " + campo + " do jogador é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O " + campo + " do jogador não pode estar vazio.");
        }
        return texto.trim();
    }

    private static void validarPosicao(double posicaoX, double posicaoY) {
        if (!Double.isFinite(posicaoX) || !Double.isFinite(posicaoY)) {
            throw new IllegalArgumentException("A posição do jogador deve conter coordenadas finitas.");
        }
    }
}
