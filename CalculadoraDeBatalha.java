import java.util.Random;

/**
 * Cuida das REGRAS da batalha: rolar o dado, ver se acertou,
 * gastar estamina e aplicar o dano (descontando a resistência).
 */
public class CalculadoraDeBatalha {

    private final Random random;

    public CalculadoraDeBatalha(Random random) {
        this.random = random;
    }

    /** Rola um "dado de 100 lados": devolve um número de 1 a 100. */
    public int rolarD100() {
        return random.nextInt(100) + 1;
    }

    /** Acerta se a rolagem for MENOR OU IGUAL à precisão (80% -> rolagens de 1 a 80). */
    public boolean acertou(Ataque ataque, int rolagem) {
        return rolagem <= ataque.getPrecisao();
    }

    /**
     * Dano real = dano do ataque - resistência do alvo, com mínimo de 1
     * (um golpe que acerta sempre machuca pelo menos um pouco).
     */
    public int calcularDano(Ataque ataque, Personagem alvo) {
        return Math.max(1, ataque.getDano() - alvo.getResistencia());
    }

    /** Executa um turno de ataque completo e mostra tudo no console. */
    public void executarAtaque(Personagem atacante, Personagem alvo, Ataque ataque) {
        // Sem ataque (null) ou golpe sem estamina: o turno é perdido.
        if (ataque == null || !ataque.temEstamina()) {
            System.out.printf("%s está sem estamina e perde o turno.%n", atacante.getNome());
            return;
        }

        ataque.consumirEstamina(); // gasta 1 uso, acertando ou errando
        int rolagem = rolarD100();

        System.out.printf("%s usa %s (dano %d | precisão %d%% | estamina %d/%d) - rolagem: %d%n",
                atacante.getNome(), ataque.getNome(), ataque.getDano(), ataque.getPrecisao(),
                ataque.getEstaminaAtual(), ataque.getEstaminaMaxima(), rolagem);

        if (acertou(ataque, rolagem)) {
            int danoFinal = calcularDano(ataque, alvo);
            alvo.receberDano(danoFinal);
            System.out.printf("  Acertou! %s perde %d de vida (dano %d - resistência %d) -> %d/%d%n",
                    alvo.getNome(), danoFinal, ataque.getDano(), alvo.getResistencia(),
                    alvo.getVidaAtual(), alvo.getVidaMaxima());
        } else {
            System.out.println("  Errou o ataque");
        }
    }
}
