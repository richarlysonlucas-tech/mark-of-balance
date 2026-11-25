package com.ousia;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SaveManager {
    private static final Path SAVE = Path.of("data", "ousia-save.properties");

    private SaveManager() {
    }

    public static void save(Player player) {
        Properties properties = new Properties();
        properties.setProperty("essence", String.valueOf(player.getEssence()));
        properties.setProperty("health", String.valueOf(player.getHealth()));
        properties.setProperty("equipped", player.getEquipped());
        properties.setProperty("upgradeLevel", String.valueOf(player.getUpgradeLevel()));
        player.getInventory().getItems().forEach(
            (id, quantity) -> properties.setProperty("item." + id, String.valueOf(quantity)));

        try {
            Files.createDirectories(SAVE.getParent());
            try (OutputStream out = Files.newOutputStream(SAVE)) {
                properties.store(out, "Ousia save");
            }
            System.out.println("Progresso salvo em " + SAVE + ".");
        } catch (IOException e) {
            System.out.println("Erro ao salvar: " + e.getMessage());
        }
    }

    public static void load(Player player) {
        if (!Files.exists(SAVE)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(SAVE)) {
            properties.load(in);
            player.setEssence(Integer.parseInt(properties.getProperty("essence", "100")));
            player.setHealth(Integer.parseInt(properties.getProperty("health", "100")));
            player.setUpgradeLevel(
                Integer.parseInt(properties.getProperty("upgradeLevel", "0")));

            player.getInventory().clear();
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("item.")) {
                    String id = key.substring("item.".length());
                    int quantity = Integer.parseInt(properties.getProperty(key));
                    player.getInventory().add(id, quantity);
                }
            }
            player.setEquipped(properties.getProperty("equipped", ""));
            System.out.println("Save carregado com sucesso.");
        } catch (IOException | NumberFormatException e) {
            System.out.println("Erro ao carregar save: " + e.getMessage());
        }
    }
}
