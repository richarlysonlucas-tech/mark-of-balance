package ousia.entity;

/**
 * Inimigo (ex.: um Corrompido). Os atributos escalam com o nivel e ele
 * concede XP ao jogador que o derrota.
 */
public class Enemy extends Entity {

    private final long xpBase;

    public Enemy(String nome, int nivel) {
        super(nome, nivel);

        int n = nivel - 1;
        stats.setBase(Stat.VIDA_MAXIMA, 40 + 14 * n)
             .setBase(Stat.ENERGIA_MAXIMA, 20 + 2 * n)
             .setBase(Stat.FORCA, 6 + 1.8 * n)
             .setBase(Stat.DEFESA, 2 + 0.8 * n)
             .setBase(Stat.VELOCIDADE, 8 + 0.3 * n);
        restoreAll();

        this.xpBase = 15 + 6L * nivel;
    }

    /**
     * XP concedido a um jogador de certo nivel. Inimigos acima do jogador rendem
     * ate +50%; muito abaixo, caem ate 10%.
     */
    public long xpPara(int nivelDoJogador) {
        double fator = 1 + 0.1 * (getNivel() - nivelDoJogador);
        fator = Math.max(0.1, Math.min(1.5, fator));
        return Math.max(1, Math.round(xpBase * fator));
    }
}
