import java.util.List;
import java.util.Random;

/**
 * Inimigo é outro tipo de Personagem.
 * Sua escolha de ataque padrão é um sorteio entre os golpes com estamina,
 * mas as classes filhas podem SOBRESCREVER (@Override) para ficarem mais espertas.
 */
public abstract class Inimigo extends Personagem {

    /** A cada melhoria do jogador, os inimigos ficam 12% mais fortes. */
    public static final double FATOR_ESCALONAMENTO = 1.12;

    public Inimigo(String nome, int vidaMaxima, int resistencia, int velocidade) {
        super(nome, vidaMaxima, resistencia, velocidade);
    }

    /**
     * Escalona o inimigo: statusFinal = Math.round(statusBase * 1.12 ^ totalMelhorias).
     * Afeta vida, resistência, velocidade, dano e estamina. A precisão NÃO muda.
     * Depois de escalonar, o inimigo começa a batalha com vida e estamina cheias.
     */
    public void escalonar(int totalMelhoriasDoJogador) {
        double mult = Math.pow(FATOR_ESCALONAMENTO, totalMelhoriasDoJogador);
        aplicarMultiplicadoresDeStatus(mult, mult, mult);
        for (Ataque ataque : getAtaques()) {
            ataque.aplicarMultiplicadores(mult, 1.0, mult); // precisão fica com multiplicador 1
        }
        restaurar();
    }

    @Override
    public Ataque escolherAtaque(Random random) {
        List<Ataque> disponiveis = getAtaquesDisponiveis();
        if (disponiveis.isEmpty()) {
            return null;
        }
        return disponiveis.get(random.nextInt(disponiveis.size()));
    }
}
