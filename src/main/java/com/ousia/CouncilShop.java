package com.ousia;

public class CouncilShop {
    private final Player player;

    public CouncilShop(Player player) {
        this.player = player;
    }

    public void show() {
        System.out.println("\n=== CONSELHO - LOJA DO HUB ===");
        for (Item item : Items.all()) {
            if (item.getBuyPrice() > 0) {
                System.out.printf("%-27s | %3d Essências | %s%n",
                    item.getName(),
                    item.getBuyPrice(),
                    item.getDescription());
            }
        }
    }

    public boolean buy(String id) {
        Item item = Items.get(id);
        if (item == null
                || item.getBuyPrice() <= 0
                || player.getEssence() < item.getBuyPrice()) {
            return false;
        }
        if (!player.getInventory().add(id, 1)) {
            return false;
        }
        player.spendEssence(item.getBuyPrice());
        return true;
    }

    public boolean sell(String id, int amount) {
        Item item = Items.get(id);
        if (amount <= 0
                || item == null
                || item.getSellPrice() <= 0
                || !player.getInventory().remove(id, amount)) {
            return false;
        }
        player.addEssence(item.getSellPrice() * amount);
        return true;
    }
}
