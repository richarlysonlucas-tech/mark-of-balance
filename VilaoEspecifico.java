/** Vilão Específico: inimigo mais forte, com golpes mais pesados. */
public class VilaoEspecifico extends Inimigo {
    public VilaoEspecifico(String nome) {
        super(nome, 32, 6, 2);
        adicionarAtaque(new Ataque("Ataque pesado", 9, 85, 8));
        adicionarAtaque(new Ataque("Ataque especial", 17, 75, 3));
    }
}
