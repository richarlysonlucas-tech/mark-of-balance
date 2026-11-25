package com.ousia;

import java.util.Scanner;

public class Main {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Player PLAYER = new Player();
    private static final CouncilShop SHOP = new CouncilShop(PLAYER);

    public static void main(String[] args) {
        SaveManager.load(PLAYER);
        System.out.println(
            "==================================\n"
            + "       OUSIA - O EQUILIBRIO\n"
            + "==================================");

        boolean running = true;
        while (running) {
            printStatus();
            System.out.println(
                "\n1 - Inventário"
                + "\n2 - Derrotar Corrompidos (simular)"
                + "\n3 - Usar Poção Vital"
                + "\n4 - Equipar/desequipar"
                + "\n5 - Loja do Conselho"
                + "\n6 - Vender item"
                + "\n7 - Aprimorar equipamento"
                + "\n8 - Salvar progresso"
                + "\n0 - Sair");

            switch (readInt("Escolha: ")) {
                case 1 -> PLAYER.getInventory().print();
                case 2 -> defeatCorrupted();
                case 3 -> usePotion();
                case 4 -> equipmentMenu();
                case 5 -> shopMenu();
                case 6 -> sellMenu();
                case 7 -> upgrade();
                case 8 -> SaveManager.save(PLAYER);
                case 0 -> {
                    SaveManager.save(PLAYER);
                    running = false;
                }
                default -> System.out.println("Opção inválida.");
            }
        }

        System.out.println("Até a próxima jornada em Ousia!");
    }

    private static void printStatus() {
        String equipmentName = "Nenhum";
        if (!PLAYER.getEquipped().isBlank()) {
            Item equipment = Items.get(PLAYER.getEquipped());
            if (equipment != null) {
                equipmentName = equipment.getName();
            }
        }

        System.out.printf(
            "%nVida: %d/%d | Essência: %d | Equipamento: %s | Poder: %d%n",
            PLAYER.getHealth(),
            PLAYER.getMaxHealth(),
            PLAYER.getEssence(),
            equipmentName,
            PLAYER.getTotalPower());
    }

    private static void defeatCorrupted() {
        int reward = 20 + (int) (Math.random() * 21);
        PLAYER.addEssence(reward);
        System.out.println("Corrompido derrotado! +" + reward + " Essências.");
        collect("fragmento", 1 + (int) (Math.random() * 3));
        if (Math.random() < 0.35) {
            collect("pocao", 1);
        }
        if (Math.random() < 0.12) {
            collect("cristal", 1);
        }
    }

    private static void collect(String id, int amount) {
        Item item = Items.get(id);
        if (item != null && PLAYER.getInventory().add(id, amount)) {
            System.out.println("Coletado: " + item.getName() + " x" + amount);
        } else {
            System.out.println("Inventário cheio! Não foi possível coletar o item.");
        }
    }

    private static void usePotion() {
        if (PLAYER.getHealth() == PLAYER.getMaxHealth()) {
            System.out.println("Sua vida já está cheia.");
        } else if (PLAYER.getInventory().remove("pocao", 1)) {
            PLAYER.heal(Items.get("pocao").getHealAmount());
            System.out.println("Poção usada. Vida: " + PLAYER.getHealth());
        } else {
            System.out.println("Você não possui Poção Vital.");
        }
    }

    private static void equipmentMenu() {
        System.out.println("IDs disponíveis no inventário:");
        for (Item item : Items.all()) {
            if (item.getType() == ItemType.EQUIPMENT
                    && PLAYER.getInventory().getQuantity(item.getId()) > 0) {
                System.out.println(item.getName() + " - ID: " + item.getId());
            }
        }

        String id = readLine("ID do equipamento ou 'sair' para desequipar: ").toLowerCase();
        if (id.equals("sair")) {
            PLAYER.unequip();
            System.out.println("Desequipado.");
        } else if (PLAYER.equip(id)) {
            System.out.println("Equipado!");
        } else {
            System.out.println("Não foi possível equipar. Confira o ID e o inventário.");
        }
    }

    private static void shopMenu() {
        SHOP.show();
        System.out.println("IDs: pocao, fragmento, cristal, purificacao, espada, armadura");
        String id = readLine("Comprar (ou 'voltar'): ").toLowerCase();
        if (!id.equals("voltar")) {
            System.out.println(
                SHOP.buy(id)
                    ? "Compra realizada!"
                    : "Compra não realizada: saldo ou espaço insuficiente.");
        }
    }

    private static void sellMenu() {
        PLAYER.getInventory().print();
        String id = readLine("ID do item: ").toLowerCase();
        int amount = readInt("Quantidade: ");
        if (amount <= 0) {
            System.out.println("Informe uma quantidade válida.");
        } else if (SHOP.sell(id, amount)) {
            System.out.println("Venda realizada!");
        } else {
            System.out.println("Não foi possível vender esse item.");
        }
    }

    private static void upgrade() {
        if (PLAYER.upgrade()) {
            System.out.println(
                "Aprimoramento concluído! Poder: " + PLAYER.getTotalPower());
        } else {
            System.out.println(
                "Precisa de equipamento equipado, 3 Fragmentos e 30 Essências.");
        }
    }

    private static int readInt(String message) {
        System.out.print(message);
        try {
            return Integer.parseInt(SCANNER.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readLine(String message) {
        System.out.print(message);
        return SCANNER.nextLine().trim();
    }
}
