package modelo;

import java.util.Objects;

/**
 * Guarda o resultado de uma tentativa de encontro.
 */
public class Encontro {
    private final boolean ocorreu;
    private final Jogador jogador;
    private final Inimigo inimigo;
    private final String origem;

    private Encontro(boolean ocorreu, Jogador jogador, Inimigo inimigo, String origem) {
        this.jogador = Objects.requireNonNull(jogador, "O jogador do encontro é obrigatório.");
        this.origem = Objects.requireNonNull(origem, "A origem do encontro é obrigatória.");
        this.ocorreu = ocorreu;
        this.inimigo = inimigo;
    }

    public static Encontro semEncontro(Jogador jogador) {
        return new Encontro(false, jogador, null, "NENHUM");
    }

    public static Encontro comEncontro(Jogador jogador, Inimigo inimigo) {
        Objects.requireNonNull(inimigo, "O inimigo do encontro é obrigatório.");
        return new Encontro(true, jogador, inimigo, "ALEATORIO");
    }

    public static Encontro comEncontro(Jogador jogador, Inimigo inimigo, String origem) {
        Objects.requireNonNull(inimigo, "O inimigo do encontro é obrigatório.");
        if (origem == null || origem.trim().isEmpty()) {
            throw new IllegalArgumentException("A origem do encontro não pode estar vazia.");
        }
        return new Encontro(true, jogador, inimigo, origem);
    }

    public boolean ocorreu() {
        return ocorreu;
    }

    public Jogador getJogador() {
        return jogador;
    }

    public Inimigo getInimigo() {
        return inimigo;
    }

    public String getOrigem() {
        return origem;
    }
}
