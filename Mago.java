/** Mago: herói de cajado. */
public class Mago extends Heroi {
    public Mago(String nome) {
        super(nome, 14, 1, 3, "Magia");
        adicionarAtaque(new Ataque("Ataque simples", 5, 100));
        adicionarAtaque(new Ataque("Ataque pesado", 15, 80, 6));
        adicionarAtaque(new Ataque("Ataque especial", 23, 65, 3));
    }
}
