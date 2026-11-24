package ousia.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import ousia.progression.ExperienceCurve;
import ousia.progression.Progression;

/**
 * O heroi: atributos e progressao de nivel.
 */
public class Player extends Entity {

    private static final int NIVEL_MAXIMO = 50;

    private final Progression progressao;
    private final List<Consumer<Integer>> ouvintesDeNivel =
            new ArrayList<Consumer<Integer>>();
    private int pontosDeHabilidade;

    public Player(String nome) {
        super(nome, 1);
        this.progressao = new Progression(
                ExperienceCurve.polinomial(100, 1.5), NIVEL_MAXIMO, 1);

        stats.setBase(Stat.VIDA_MAXIMA, 100)
             .setBase(Stat.ENERGIA_MAXIMA, 50)
             .setBase(Stat.FORCA, 10)
             .setBase(Stat.DEFESA, 5)
             .setBase(Stat.VELOCIDADE, 10);
        restoreAll();
    }

    /** Registra um callback chamado a cada novo nivel alcançado. */
    public void aoSubirDeNivel(Consumer<Integer> ouvinte) {
        ouvintesDeNivel.add(ouvinte);
    }

    /**
     * Concede XP e aplica crescimento para cada nivel ganho.
     *
     * @return quantidade de niveis ganhos
     */
    public int ganharXp(long xp) {
        List<Integer> novosNiveis = progressao.addXp(xp);
        for (int nivel : novosNiveis) {
            setNivel(nivel);
            aplicarCrescimentoDeNivel();
            pontosDeHabilidade++;
        }
        if (!novosNiveis.isEmpty()) {
            restoreAll();
            for (int nivel : novosNiveis) {
                for (Consumer<Integer> ouvinte : ouvintesDeNivel) {
                    ouvinte.accept(nivel);
                }
            }
        }
        return novosNiveis.size();
    }

    /** Concede ao jogador o XP de um inimigo morto. */
    public int derrotar(Enemy inimigo) {
        if (inimigo.isAlive()) {
            throw new IllegalStateException(
                    inimigo.getNome() + " ainda esta vivo");
        }
        return ganharXp(inimigo.xpPara(getNivel()));
    }

    private void aplicarCrescimentoDeNivel() {
        stats.addBase(Stat.VIDA_MAXIMA, 12);
        stats.addBase(Stat.ENERGIA_MAXIMA, 4);
        stats.addBase(Stat.FORCA, 2);
        stats.addBase(Stat.DEFESA, 1.5);
        stats.addBase(Stat.VELOCIDADE, 0.5);
    }

    /** Gasta pontos de habilidade. */
    public boolean gastarPontosDeHabilidade(int quantidade) {
        if (quantidade <= 0 || quantidade > pontosDeHabilidade) {
            return false;
        }
        pontosDeHabilidade -= quantidade;
        return true;
    }

    public Progression getProgressao() {
        return progressao;
    }

    public int getPontosDeHabilidade() {
        return pontosDeHabilidade;
    }

    /** Ficha de status para debug ou tela de status. */
    public String resumo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("== %s | Nv %d | XP %d/%d | Pontos: %d ==%n",
                getNome(), getNivel(), progressao.getXpAtual(),
                progressao.xpParaProximoNivel(), pontosDeHabilidade));
        for (Stat s : Stat.values()) {
            sb.append(String.format("  %-12s %.1f%n",
                    s.getRotulo(), stats.get(s)));
        }
        return sb.toString().trim();
    }
}
