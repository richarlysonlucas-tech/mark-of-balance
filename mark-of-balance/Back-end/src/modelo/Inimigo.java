package modelo;

import java.util.Objects;

/**
 * Representa um inimigo que pode participar de um encontro.
 */
public class Inimigo {
    private final String id;
    private final String nome;
    private final int nivel;

    public Inimigo(String id, String nome, int nivel) {
        this.id = validarTexto(id, "id");
        this.nome = validarTexto(nome, "nome");
        if (nivel <= 0) {
            throw new IllegalArgumentException("O nível do inimigo deve ser positivo.");
        }
        this.nivel = nivel;
    }

    private static String validarTexto(String texto, String campo) {
        Objects.requireNonNull(texto, "O " + campo + " do inimigo é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O " + campo + " do inimigo não pode estar vazio.");
        }
        return texto.trim();
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getNivel() {
        return nivel;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Inimigo)) {
            return false;
        }
        Inimigo inimigo = (Inimigo) outro;
        return id.equals(inimigo.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
