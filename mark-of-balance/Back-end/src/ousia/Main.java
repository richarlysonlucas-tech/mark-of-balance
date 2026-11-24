package ousia;

import ousia.entity.Enemy;
import ousia.entity.Player;
import ousia.entity.Stat;
import ousia.entity.StatModifier;

/**
 * Demo de status e XP: o heroi derrota inimigos, ganha XP e sobe de nivel.
 */
public class Main {

    public static void main(String[] args) {
        Player heroi = new Player("Heroi de Ousia");
        heroi.aoSubirDeNivel(nivel ->
                System.out.println("  >>> SUBIU PARA O NIVEL " + nivel + "!"));

        System.out.println(heroi.resumo());

        // Equipamento entra como modificador de status: espada +6 de Forca.
        heroi.addModifier(StatModifier.flat("espada_de_ferro", Stat.FORCA, 6));

        System.out.println("\n--- Derrotando inimigos ---");
        for (int i = 1; i <= 15; i++) {
            Enemy inimigo = new Enemy("Corrompido #" + i, 1 + i / 3);
            inimigo.takeDamage(Integer.MAX_VALUE);
            long xp = inimigo.xpPara(heroi.getNivel());
            heroi.derrotar(inimigo);
            System.out.printf("%s (Nv %d) derrotado: +%d XP%n",
                    inimigo.getNome(), inimigo.getNivel(), xp);
        }

        System.out.println();
        System.out.println(heroi.resumo());
        System.out.printf("Barra de XP: %.0f%%%n",
                heroi.getProgressao().progressoDoNivel() * 100);
    }
}
