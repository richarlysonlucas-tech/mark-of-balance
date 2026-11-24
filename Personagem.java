import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Classe BASE (abstrata) de todo mundo que luta: heróis e inimigos.
 * Guarda o que é COMUM a todos: nome, vida, resistência, velocidade e ataques.
 *
 * Mesma ideia dos ataques: guardamos o valor BASE e o valor ATUAL de cada status.
 */
public abstract class Personagem {

    private final String nome;

    // Valores base (originais)
    private final int vidaMaximaBase;
    private final int resistenciaBase;
    private final int velocidadeBase;

    // Valores atuais (com bônus)
    private int vidaMaxima;
    private int vidaAtual;
    private int resistencia;
    private int velocidade;

    private final List<Ataque> ataques = new ArrayList<>();

    /** Construtor antigo: continua funcionando (resistência 0, velocidade 1). */
    public Personagem(String nome, int vidaMaxima) {
        this(nome, vidaMaxima, 0, 1);
    }

    public Personagem(String nome, int vidaMaxima, int resistencia, int velocidade) {
        this.nome = nome;
        this.vidaMaximaBase = vidaMaxima;
        this.resistenciaBase = resistencia;
        this.velocidadeBase = velocidade;

        this.vidaMaxima = vidaMaxima;
        this.vidaAtual = vidaMaxima; // começa com a vida cheia
        this.resistencia = resistencia;
        this.velocidade = velocidade;
    }

    // "protected": só as classes filhas podem chamar.
    protected void adicionarAtaque(Ataque ataque) {
        ataques.add(ataque);
    }

    /**
     * POLIMORFISMO: cada tipo de personagem decide do seu jeito qual ataque usar.
     * Retorna null se NENHUM ataque tiver estamina (o turno é perdido).
     */
    public abstract Ataque escolherAtaque(Random random);

    /** Reduz a vida. Math.max garante que a vida nunca fique negativa. */
    public void receberDano(int dano) {
        vidaAtual = Math.max(0, vidaAtual - dano);
    }

    public boolean estaVivo() {
        return vidaAtual > 0;
    }

    /**
     * Recalcula vida, resistência e velocidade a partir dos valores base.
     * Se a vida máxima aumentou, a vida atual aumenta junto (o ganho vem "cheio").
     */
    protected void aplicarMultiplicadoresDeStatus(double multVida, double multResistencia, double multVelocidade) {
        int vidaMaximaAntiga = vidaMaxima;
        vidaMaxima = Math.max(1, (int) Math.round(vidaMaximaBase * multVida));
        resistencia = Math.max(0, (int) Math.round(resistenciaBase * multResistencia));
        velocidade = Math.max(1, (int) Math.round(velocidadeBase * multVelocidade));

        int ajustada = vidaAtual + (vidaMaxima - vidaMaximaAntiga);
        vidaAtual = Math.max(0, Math.min(vidaMaxima, ajustada));
    }

    /** Vida e estamina de todos os ataques voltam ao máximo. */
    public void restaurar() {
        vidaAtual = vidaMaxima;
        for (Ataque ataque : ataques) {
            ataque.recuperarEstamina();
        }
    }

    /** Ataques que ainda têm estamina para serem usados. */
    protected List<Ataque> getAtaquesDisponiveis() {
        List<Ataque> disponiveis = new ArrayList<>();
        for (Ataque ataque : ataques) {
            if (ataque.temEstamina()) {
                disponiveis.add(ataque);
            }
        }
        return disponiveis;
    }

    /** Texto com os status e os ataques (usado para mostrar no console). */
    public String getResumoStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s | Vida %d/%d | Resistência %d | Velocidade %d%n",
                nome, vidaAtual, vidaMaxima, resistencia, velocidade));
        for (Ataque a : ataques) {
            sb.append(String.format("   - %-18s dano %2d | precisão %3d%% | estamina %d/%d%n",
                    a.getNome(), a.getDano(), a.getPrecisao(),
                    a.getEstaminaAtual(), a.getEstaminaMaxima()));
        }
        return sb.toString();
    }

    public String getNome()          { return nome; }
    public int getVidaAtual()        { return vidaAtual; }
    public int getVidaMaxima()       { return vidaMaxima; }
    public int getResistencia()      { return resistencia; }
    public int getVelocidade()       { return velocidade; }
    public List<Ataque> getAtaques() { return ataques; }
}
