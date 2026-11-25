package com.ousia;

public class Player {
    private static final int MAX_HEALTH = 100;
    private static final int INVENTORY_CAPACITY = 30;

    private final Inventory inventory = new Inventory(INVENTORY_CAPACITY);
    private int essence = 100;
    private int health = MAX_HEALTH;
    private int upgradeLevel = 0;
    private String equipped = "";

    public Inventory getInventory() {
        return inventory;
    }

    public int getEssence() {
        return essence;
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return MAX_HEALTH;
    }

    public String getEquipped() {
        return equipped;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    public void addEssence(int amount) {
        essence += Math.max(0, amount);
    }

    public boolean spendEssence(int amount) {
        if (amount < 0 || essence < amount) {
            return false;
        }
        essence -= amount;
        return true;
    }

    public void heal(int amount) {
        health = Math.min(MAX_HEALTH, health + Math.max(0, amount));
    }

    public boolean equip(String id) {
        Item item = Items.get(id);
        if (item == null || item.getType() != ItemType.EQUIPMENT
                || inventory.getQuantity(id) == 0) {
            return false;
        }
        equipped = id;
        return true;
    }

    public void unequip() {
        equipped = "";
    }

    public boolean upgrade() {
        if (equipped.isBlank()
                || inventory.getQuantity("fragmento") < 3
                || essence < 30) {
            return false;
        }
        inventory.remove("fragmento", 3);
        spendEssence(30);
        upgradeLevel++;
        return true;
    }

    public int getTotalPower() {
        Item item = Items.get(equipped);
        return (item == null ? 0 : item.getPower()) + upgradeLevel;
    }

    public void setEssence(int amount) {
        essence = Math.max(0, amount);
    }

    public void setHealth(int amount) {
        health = Math.max(1, Math.min(MAX_HEALTH, amount));
    }

    public void setUpgradeLevel(int level) {
        upgradeLevel = Math.max(0, level);
    }

    public void setEquipped(String id) {
        Item item = Items.get(id);
        if (item != null
                && item.getType() == ItemType.EQUIPMENT
                && inventory.getQuantity(id) > 0) {
            equipped = id;
        } else {
            equipped = "";
        }
    }
}
