/**
 * Representa UM golpe que um personagem pode usar.
 *
 * Guardamos DOIS conjuntos de valores:
 *  - os valores BASE (originais, nunca mudam);
 *  - os valores ATUAIS (base multiplicada pelos bônus de melhoria/escalonamento).
 * Sempre recalculamos a partir do valor base. Assim, multiplicar várias vezes
 * não acumula erros de arredondamento.
 */
public class Ataque {

    /** Estamina usada quando o construtor de 3 parâmetros é chamado. */
    public static final int ESTAMINA_PADRAO = 10;

    private final String nome;

    // Valores originais (imutáveis: "final")
    private final int danoBase;
    private final int precisaoBase;
    private final int estaminaMaximaBase;

    // Valores atuais (já com bônus aplicados)
    private int dano;
    private int precisao;
    private int estaminaMaxima;
    private int estaminaAtual; // quantos usos ainda restam

    /** Construtor antigo: continua funcionando, com estamina padrão. */
    public Ataque(String nome, int dano, int precisao) {
        this(nome, dano, precisao, ESTAMINA_PADRAO);
    }

    /** Nova sobrecarga: permite definir a estamina máxima (nº de usos). */
    public Ataque(String nome, int dano, int precisao, int estaminaMaxima) {
        this.nome = nome;
        this.danoBase = dano;
        this.precisaoBase = precisao;
        this.estaminaMaximaBase = estaminaMaxima;

        this.dano = dano;
        this.precisao = precisao;
        this.estaminaMaxima = estaminaMaxima;
        this.estaminaAtual = estaminaMaxima; // começa com todos os usos
    }

    public String getNome()          { return nome; }
    public int getDano()             { return dano; }
    public int getPrecisao()         { return precisao; }
    public int getEstaminaMaxima()   { return estaminaMaxima; }
    public int getEstaminaAtual()    { return estaminaAtual; }

    public boolean temEstamina() {
        return estaminaAtual > 0;
    }

    /** Cada uso do golpe gasta 1 de estamina (nunca fica negativa). */
    public void consumirEstamina() {
        if (estaminaAtual > 0) {
            estaminaAtual--;
        }
    }

    /** Devolve todos os usos do golpe (ex.: ao começar uma nova batalha). */
    public void recuperarEstamina() {
        estaminaAtual = estaminaMaxima;
    }

    /**
     * Recalcula dano, precisão e estamina a partir dos valores BASE.
     * Ex.: multiplicadorDano = 1.5 ^ nível de Força.
     *
     * @param multDano     multiplicador do dano
     * @param multPrecisao multiplicador da precisão (o resultado tem teto de 100%)
     * @param multEstamina multiplicador da estamina máxima
     */
    public void aplicarMultiplicadores(double multDano, double multPrecisao, double multEstamina) {
        dano = Math.max(1, arredondar(danoBase * multDano));
        precisao = Math.min(100, arredondar(precisaoBase * multPrecisao));

        int estaminaMaximaAntiga = estaminaMaxima;
        estaminaMaxima = Math.max(1, arredondar(estaminaMaximaBase * multEstamina));

        // Se a estamina máxima subiu, o jogador ganha esses usos extras na hora.
        int ajustada = estaminaAtual + (estaminaMaxima - estaminaMaximaAntiga);
        estaminaAtual = Math.max(0, Math.min(estaminaMaxima, ajustada));
    }

    // Math.round devolve "long"; convertemos para int.
    private static int arredondar(double valor) {
        return (int) Math.round(valor);
    }
}
