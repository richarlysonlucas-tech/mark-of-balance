package com.ousia;

import java.util.LinkedHashMap;
import java.util.Map;

public class Inventory {
    private final int capacity;
    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    public Inventory(int capacity) {
        this.capacity = capacity;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getUsedSlots() {
        return quantities.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getQuantity(String id) {
        return quantities.getOrDefault(id, 0);
    }

    public boolean add(String id, int amount) {
        if (Items.get(id) == null || amount <= 0
                || getUsedSlots() + amount > capacity) {
            return false;
        }
        quantities.merge(id, amount, Integer::sum);
        return true;
    }

    public boolean remove(String id, int amount) {
        int current = getQuantity(id);
        if (amount <= 0 || current < amount) {
            return false;
        }
        if (current == amount) {
            quantities.remove(id);
        } else {
            quantities.put(id, current - amount);
        }
        return true;
    }

    public void print() {
        System.out.println("\n=== INVENTÁRIO (" + getUsedSlots() + "/" + capacity + ") ===");
        if (quantities.isEmpty()) {
            System.out.println("Inventário vazio.");
            return;
        }
        quantities.forEach((id, quantity) ->
            System.out.printf("%-27s x%-3d [%s]%n",
                Items.get(id).getName(), quantity, Items.get(id).getRarity()));
    }

    public Map<String, Integer> getItems() {
        return quantities;
    }

    public void clear() {
        quantities.clear();
    }
}
