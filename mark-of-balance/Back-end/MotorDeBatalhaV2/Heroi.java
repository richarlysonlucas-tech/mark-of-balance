import java.util.List;
import java.util.Random;

/**
 * Herói é um tipo de Personagem que pode MELHORAR seus atributos.
 *
 * São 5 atributos, cada um com níveis de 0 a 5. Cada nível custa 1 ponto
 * e multiplica os status ligados a ele por 1.5:
 *   valorFinal = Math.round(valorBase * Math.pow(1.5, nivel))
 *
 * IMPORTANTE: as classes filhas devem adicionar os ataques NESTA ORDEM:
 * simples, pesado, especial. O último ataque da lista é tratado como o especial.
 */
public abstract class Heroi extends Personagem {

    public static final int NIVEL_MAXIMO = 5;
    public static final double FATOR_MELHORIA = 1.5;

    /** Os 5 atributos melhoráveis (enum = lista fixa de opções). */
    public enum Atributo {
        FORCA("Força"),
        DEFESA("Defesa"),
        VELOCIDADE("Velocidade"),
        PRECISAO("Precisão"),
        CLASSE("Atributo de classe"); // o nome real vem da classe filha

        private final String rotulo;
        Atributo(String rotulo) { this.rotulo = rotulo; }
        public String getRotulo() { return rotulo; }
    }

    private final String raca;
    private final String nomeAtributoClasse; // "Habilidade com espada", "Magia"...

    // niveis[Atributo.FORCA.ordinal()] guarda o nível de Força, e assim por diante
    private final int[] niveis = new int[Atributo.values().length];
    private int pontosDisponiveis = 0;

    public Heroi(String nome, int vidaMaxima, int resistencia, int velocidade, String nomeAtributoClasse) {
        super(nome, vidaMaxima, resistencia, velocidade);
        this.raca = "Humano";
        this.nomeAtributoClasse = nomeAtributoClasse;
    }

    // ---------- Pontos e melhorias ----------

    /** O herói recebe pontos para gastar (ex.: ao subir de nível). */
    public void ganharPontos(int quantidade) {
        if (quantidade > 0) {
            pontosDisponiveis += quantidade;
        }
    }

    /**
     * Tenta gastar 1 ponto no atributo escolhido.
     * @return true se a melhoria foi feita; false se não há pontos ou o atributo já está no nível 5.
     */
    public boolean melhorarAtributo(Atributo atributo) {
        int indice = atributo.ordinal();
        if (pontosDisponiveis <= 0 || niveis[indice] >= NIVEL_MAXIMO) {
            return false;
        }
        pontosDisponiveis--;
        niveis[indice]++;
        aplicarAtributos(); // recalcula todos os status com o novo nível
        return true;
    }

    /** Soma dos níveis de todos os atributos = nº de pontos gastos (usado no escalonamento dos inimigos). */
    public int getTotalMelhorias() {
        int total = 0;
        for (int nivel : niveis) {
            total += nivel;
        }
        return total;
    }

    public int getNivel(Atributo atributo)   { return niveis[atributo.ordinal()]; }
    public int getPontosDisponiveis()        { return pontosDisponiveis; }
    public String getRaca()                  { return raca; }
    public String getNomeAtributoClasse()    { return nomeAtributoClasse; }

    /** Multiplicador 1.5 elevado ao nível do atributo. */
    private double multiplicador(Atributo atributo) {
        return Math.pow(FATOR_MELHORIA, niveis[atributo.ordinal()]);
    }

    /**
     * Recalcula tudo a partir dos valores base:
     *  - Defesa     -> vida máxima, resistência e estamina máxima dos ataques
     *  - Velocidade -> velocidade
     *  - Força      -> dano dos ataques simples e pesado (todos menos o último)
     *  - Precisão   -> precisão de todos os ataques (teto de 100%)
     *  - Classe     -> dano do ataque especial (o último)
     */
    private void aplicarAtributos() {
        double multDefesa = multiplicador(Atributo.DEFESA);
        aplicarMultiplicadoresDeStatus(multDefesa, multDefesa, multiplicador(Atributo.VELOCIDADE));

        List<Ataque> lista = getAtaques();
        for (int i = 0; i < lista.size(); i++) {
            boolean ehEspecial = (i == lista.size() - 1);
            double multDano = ehEspecial ? multiplicador(Atributo.CLASSE) : multiplicador(Atributo.FORCA);
            lista.get(i).aplicarMultiplicadores(multDano, multiplicador(Atributo.PRECISAO), multDefesa);
        }
    }

    // ---------- Painel visual ----------

    /** Mostra os 5 atributos com quadrados, ex.: [■][■][□][□][□] 2/5 */
    public String getPainelAtributos() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("== %s (%s) | Pontos disponíveis: %d ==%n",
                getNome(), raca, pontosDisponiveis));
        for (Atributo atributo : Atributo.values()) {
            String rotulo = (atributo == Atributo.CLASSE) ? nomeAtributoClasse : atributo.getRotulo();
            int nivel = getNivel(atributo);
            sb.append(String.format("%-24s %s %d/%d%n", rotulo, quadrados(nivel), nivel, NIVEL_MAXIMO));
        }
        return sb.toString();
    }

    private String quadrados(int nivel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NIVEL_MAXIMO; i++) {
            sb.append(i < nivel ? "[■]" : "[□]");
        }
        return sb.toString();
    }

    // ---------- Escolha de ataque ----------

    /**
     * No jogo real, quem escolhe o golpe é o JOGADOR (clicando num botão).
     * Na simulação, sorteamos entre os ataques que ainda têm estamina.
     * Retorna null se todos estiverem sem estamina.
     */
    @Override
    public Ataque escolherAtaque(Random random) {
        List<Ataque> disponiveis = getAtaquesDisponiveis();
        if (disponiveis.isEmpty()) {
            return null;
        }
        return disponiveis.get(random.nextInt(disponiveis.size()));
    }
}
