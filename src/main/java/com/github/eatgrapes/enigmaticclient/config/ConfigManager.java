package com.github.eatgrapes.enigmaticclient.config;

import com.github.eatgrapes.enigmaticclient.module.Module;
import com.github.eatgrapes.enigmaticclient.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class ConfigManager {
    private static final File CONFIG_DIR = new File("config/enigmaticclient");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean isDirty = false;

    /**
     * Mark the config as dirty (needs saving).
     */
    public static void markDirty() {
        isDirty = true;
    }

    /**
     * Load the config from file.
     */
    public static void loadConfig() {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        if (!CONFIG_FILE.exists()) {
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject config = new JsonParser().parse(reader).getAsJsonObject();
            if (config == null) return;

            // Load module states
            if (config.has("modules")) {
                JsonObject modules = config.getAsJsonObject("modules");
                for (Map.Entry<String, Module> entry : ModuleManager.getInstance().getModules().entrySet()) {
                    String moduleName = entry.getKey();
                    if (modules.has(moduleName)) {
                        boolean enabled = modules.get(moduleName).getAsBoolean();
                        entry.getValue().setEnabled(enabled);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save the config to file.
     */
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            JsonObject config = new JsonObject();

            // Save module states
            JsonObject modules = new JsonObject();
            for (Map.Entry<String, Module> entry : ModuleManager.getInstance().getModules().entrySet()) {
                modules.addProperty(entry.getKey(), entry.getValue().isEnabled());
            }
            config.add("modules", modules);

            writer.write(GSON.toJson(config));
            isDirty = false;
            System.out.println("[Enigmatic] Config saved to disk");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Save the config on shutdown (if dirty).
     */
    public static void saveOnShutdown() {
        if (isDirty) {
            saveConfig();
        }
    }
}