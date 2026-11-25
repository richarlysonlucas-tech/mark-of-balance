import java.util.Random;

/**
 * Boss: o chefão.
 * POLIMORFISMO: lógica própria de escolha de ataque, diferente do sorteio de Inimigo.
 */
public class Boss extends Inimigo {

    public Boss(String nome) {
        super(nome, 100, 10, 5);
        adicionarAtaque(new Ataque("Ataque especial", 15, 100, 8)); // índice 0
        adicionarAtaque(new Ataque("Ataque supremo", 25, 85, 4));   // índice 1
    }

    /**
     * 50% de chance de preferir o supremo; no resto, o especial.
     * Se o golpe preferido estiver sem estamina, usa o outro.
     * Se os dois estiverem sem estamina, retorna null (perde o turno).
     */
    @Override
    public Ataque escolherAtaque(Random random) {
        boolean preferirSupremo = random.nextInt(100) < 50;
        Ataque preferido = getAtaques().get(preferirSupremo ? 1 : 0);
        Ataque alternativo = getAtaques().get(preferirSupremo ? 0 : 1);

        if (preferido.temEstamina()) {
            return preferido;
        }
        if (alternativo.temEstamina()) {
            return alternativo;
        }
        return null;
    }
}
