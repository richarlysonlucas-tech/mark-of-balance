/** Vilão Genérico: inimigo fraco e comum. */
public class VilaoGenerico extends Inimigo {
    public VilaoGenerico(String nome) {
        super(nome, 20, 5, 1);
        adicionarAtaque(new Ataque("Ataque simples", 5, 90));
        adicionarAtaque(new Ataque("Ataque especial", 12, 75, 3));
    }
}
