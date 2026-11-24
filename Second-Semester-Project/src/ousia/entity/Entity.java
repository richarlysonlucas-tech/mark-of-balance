package ousia.entity;

import java.util.UUID;

/**
 * Base de qualquer ser do jogo (jogador e inimigos).
 * Guarda identidade, nivel, atributos e recursos atuais.
 */
public abstract class Entity {

    private final UUID id = UUID.randomUUID();
    private final String nome;
    private int nivel;
    protected final StatSheet stats = new StatSheet();
    private int vida;
    private int energia;

    protected Entity(String nome, int nivel) {
        if (nivel < 1) {
            throw new IllegalArgumentException("nivel minimo e 1");
        }
        this.nome = nome;
        this.nivel = nivel;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getNivel() {
        return nivel;
    }

    protected void setNivel(int nivel) {
        this.nivel = nivel;
    }

    public StatSheet getStats() {
        return stats;
    }

    public int getVida() {
        return vida;
    }

    public int getVidaMaxima() {
        return stats.getInt(Stat.VIDA_MAXIMA);
    }

    public int getEnergia() {
        return energia;
    }

    public int getEnergiaMaxima() {
        return stats.getInt(Stat.ENERGIA_MAXIMA);
    }

    public boolean isAlive() {
        return vida > 0;
    }

    /** Enche vida e energia ao nascer, subir de nivel ou descansar. */
    public void restoreAll() {
        vida = getVidaMaxima();
        energia = getEnergiaMaxima();
    }

    /** Garante que vida e energia atuais nao passem do maximo. */
    protected void refreshResources() {
        vida = Math.min(vida, getVidaMaxima());
        energia = Math.min(energia, getEnergiaMaxima());
    }

    public void addModifier(StatModifier modifier) {
        stats.addModifier(modifier);
        refreshResources();
    }

    public void removeModifiersFrom(String source) {
        stats.removeModifiersFrom(source);
        refreshResources();
    }

    /** Aplica dano bruto e retorna o dano realmente causado. */
    public int takeDamage(int danoBruto) {
        if (!isAlive()) {
            return 0;
        }
        int dano = Math.max(1, danoBruto - stats.getInt(Stat.DEFESA));
        vida = Math.max(0, vida - dano);
        return dano;
    }

    public int heal(int quantidade) {
        if (!isAlive() || quantidade <= 0) {
            return 0;
        }
        int antes = vida;
        vida = Math.min(getVidaMaxima(), vida + quantidade);
        return vida - antes;
    }

    @Override
    public String toString() {
        return String.format("%s (Nv %d) HP %d/%d EN %d/%d",
                nome, nivel, vida, getVidaMaxima(), energia, getEnergiaMaxima());
    }
}
