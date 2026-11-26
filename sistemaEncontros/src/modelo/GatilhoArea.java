package modelo;

import java.util.List;
import java.util.Objects;

/**
 * Gatilho que é ativado quando o jogador entra em uma área retangular.
 */
public class GatilhoArea implements Gatilho {
    private final String id;
    private final String areaId;
    private final int prioridade;
    private final double xInicial;
    private final double yInicial;
    private final double largura;
    private final double altura;
    private final Inimigo inimigo;

    public GatilhoArea(String id, String areaId, int prioridade, double xInicial, double yInicial,
            double largura, double altura, Inimigo inimigo) {
        this.id = validarTexto(id, "id");
        this.areaId = validarTexto(areaId, "areaId");
        if (prioridade < 0) {
            throw new IllegalArgumentException("A prioridade do gatilho não pode ser negativa.");
        }
        this.prioridade = prioridade;
        if (!Double.isFinite(xInicial) || !Double.isFinite(yInicial)
                || !Double.isFinite(largura) || !Double.isFinite(altura)
                || largura < 0 || altura < 0) {
            throw new IllegalArgumentException("As coordenadas e dimensões do gatilho devem ser finitas e não negativas.");
        }
        this.xInicial = xInicial;
        this.yInicial = yInicial;
        this.largura = largura;
        this.altura = altura;
        this.inimigo = Objects.requireNonNull(inimigo, "O gatilho deve possuir um inimigo.");
    }

    private static String validarTexto(String texto, String campo) {
        Objects.requireNonNull(texto, "O " + campo + " do gatilho é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O " + campo + " do gatilho não pode estar vazio.");
        }
        return texto.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPrioridade() {
        return prioridade;
    }

    @Override
    public boolean foiAtivado(Jogador jogador, List<String> acoesRealizadas) {
        return jogador.getPosicaoX() >= xInicial
            && jogador.getPosicaoX() <= xInicial + largura
            && jogador.getPosicaoY() >= yInicial
            && jogador.getPosicaoY() <= yInicial + altura;
    }

    @Override
    public boolean pertenceA(String outraAreaId) {
        return areaId.equals(outraAreaId);
    }

    @Override
    public Inimigo getInimigo() {
        return inimigo;
    }
}
