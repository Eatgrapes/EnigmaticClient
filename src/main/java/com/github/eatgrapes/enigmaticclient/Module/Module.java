package com.github.eatgrapes.enigmaticclient.module;

/**
 * Abstract base class for modules.
 * Modules can be enabled/disabled and define their behavior on state changes.
 */
public abstract class Module {
    private final String name;
    private boolean enabled;

    public Module(String name) {
        this.name = name;
        this.enabled = false;
    }

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }

    /**
     * Sets the enabled state of the module.
     * Triggers onEnable() or onDisable() based on the state change.
     * @param enabled Whether the module should be enabled.
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) onEnable();
            else onDisable();
        }
    }

    /**
     * Called when the module is enabled.
     * Override to define activation logic.
     */
    public abstract void onEnable();

    /**
     * Called when the module is disabled.
     * Override to define deactivation logic.
     */
    public abstract void onDisable();

    /**
     * Called every client tick when the module is enabled.
     * Override to define persistent update logic.
     */
    public void onUpdate() {}
}