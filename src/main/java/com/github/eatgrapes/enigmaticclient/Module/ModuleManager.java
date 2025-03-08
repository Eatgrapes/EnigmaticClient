package com.github.eatgrapes.enigmaticclient.module;

import com.github.eatgrapes.enigmaticclient.module.modules.FullBright;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final Map<String, Module> modules = new HashMap<>();

    public ModuleManager() {
        registerModule(new FullBright());
    }

    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    public void registerModule(Module module) {
        modules.put(module.getName().toLowerCase(), module);
    }

    public boolean moduleExists(String name) {
        return modules.containsKey(name.toLowerCase());
    }

    public Optional<Module> getModule(String name) {
        return Optional.ofNullable(modules.get(name.toLowerCase()));
    }

    public Map<String, Module> getModules() {
        return new HashMap<>(modules);
    }

    public void initializeModules() {
        modules.values().forEach(module -> {
            if (module.isEnabled()) module.onEnable();
        });
    }
}