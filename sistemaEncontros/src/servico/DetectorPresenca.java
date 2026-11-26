package servico;

import java.util.Objects;
import modelo.AreaEncontro;
import modelo.Jogador;
import modelo.EntidadeMundo;

/**
 * Verifica se uma posição está dentro dos limites de uma área de encontro.
 */
public class DetectorPresenca {
    public boolean estaNaArea(double x, double y, AreaEncontro area) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }
        Objects.requireNonNull(area, "A área é obrigatória.");
        boolean dentroX = x >= area.getXInicial()
            && x <= area.getXInicial() + area.getLargura();
        boolean dentroY = y >= area.getYInicial()
            && y <= area.getYInicial() + area.getAltura();
        return dentroX && dentroY;
    }

    /**
     * Distância Euclidiana: raiz quadrada de (dx² + dy²).
     * O raio indica quantas unidades da tela são consideradas proximidade.
     */
    public double calcularDistancia(Jogador jogador, EntidadeMundo entidade) {
        Objects.requireNonNull(jogador, "O jogador é obrigatório.");
        Objects.requireNonNull(entidade, "A entidade é obrigatória.");
        double diferencaX = jogador.getPosicaoX() - entidade.getPosicaoX();
        double diferencaY = jogador.getPosicaoY() - entidade.getPosicaoY();
        return Math.sqrt(diferencaX * diferencaX + diferencaY * diferencaY);
    }

    public boolean estaProximo(Jogador jogador, EntidadeMundo entidade) {
        return calcularDistancia(jogador, entidade) <= entidade.getRaioDeteccao();
    }
}
