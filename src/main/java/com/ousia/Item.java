package com.ousia;

public class Item {
    private final String id;
    private final String name;
    private final String description;
    private final ItemType type;
    private final Rarity rarity;
    private final int buyPrice;
    private final int sellPrice;
    private final int healAmount;
    private final int power;

    public Item(String id, String name, String description, ItemType type, Rarity rarity,
                int buyPrice, int sellPrice, int healAmount, int power) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.rarity = rarity;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.healAmount = healAmount;
        this.power = power;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ItemType getType() {
        return type;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public int getBuyPrice() {
        return buyPrice;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public int getHealAmount() {
        return healAmount;
    }

    public int getPower() {
        return power;
    }
}
