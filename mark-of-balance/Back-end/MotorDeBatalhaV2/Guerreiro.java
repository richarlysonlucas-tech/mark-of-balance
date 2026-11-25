/** Guerreiro: herói de espada. */
public class Guerreiro extends Heroi {
    public Guerreiro(String nome) {
        // vida 20, resistência 5, velocidade 2; o atributo de classe é "Habilidade com espada"
        super(nome, 20, 5, 2, "Habilidade com espada");
        // Ordem obrigatória: simples, pesado, especial. (estamina: 3º parâmetro extra)
        adicionarAtaque(new Ataque("Ataque simples", 7, 100));      // estamina padrão
        adicionarAtaque(new Ataque("Ataque pesado", 15, 80, 6));
        adicionarAtaque(new Ataque("Ataque especial", 21, 65, 3));
    }
}
