package com.github.eatgrapes.enigmaticclient;

import com.github.eatgrapes.enigmaticclient.config.ConfigManager;
import com.github.eatgrapes.enigmaticclient.module.Module;
import com.github.eatgrapes.enigmaticclient.module.ModuleManager;
import com.github.eatgrapes.enigmaticclient.ui.ClickguiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(
    modid = "enigmaticclient",
    version = "1.0",
    clientSideOnly = true,
    acceptedMinecraftVersions = "[1.8.9]"
)
public class EnigmaticClient {
    // Color constants
    private static final String WHITE = EnumChatFormatting.WHITE.toString();
    private static final String PURPLE = EnumChatFormatting.DARK_PURPLE.toString();
    private static final String GRAY = EnumChatFormatting.GRAY.toString();
    private static final String RED = EnumChatFormatting.RED.toString();
    private static final String YELLOW = EnumChatFormatting.YELLOW.toString();
    private static final String GREEN = EnumChatFormatting.GREEN.toString();

    // Message prefix
    private static final String MOD_PREFIX = WHITE + "[" + PURPLE + "Enigmatic" + WHITE + "] ";
    private static final String UNKNOWN_CMD = MOD_PREFIX + WHITE + "Unknown command, use " + PURPLE + ".eni help";
    private static final String HELP_MSG = MOD_PREFIX + WHITE + "Available commands:\n" +
        PURPLE + ".eni help" + WHITE + " - Show help\n" +
        PURPLE + ".eni list" + WHITE + " - List modules\n" +
        PURPLE + ".eni enable <module>" + WHITE + " - Enable module\n" +
        PURPLE + ".eni disable <module>" + WHITE + " - Disable module\n" +
        PURPLE + ".eni stop" + WHITE + " - " + RED + "Force exit client";

    // State tracking variables
    private static boolean wasInWorld = false;
    private static boolean hasSavedOnWorldExit = false;
    private static long lastSaveTime = 0; // Cooldown for saving

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModuleManager.getInstance().initializeModules();
        ConfigManager.loadConfig();
        MinecraftForge.EVENT_BUS.register(this);
        Keyboard.enableRepeatEvents(true); // Enable keyboard repeat for GUI input
        System.out.println("[Enigmatic] Client initialized");
    }

    // ================================== Event Handlers ================================== //
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean isInWorld = mc.theWorld != null;

            // 1. Save config when exiting world/server
            if (wasInWorld && !isInWorld) {
                if (!hasSavedOnWorldExit) {
                    saveConfigWithCooldown();
                    System.out.println("[Enigmatic] Config saved (exiting world)");
                    hasSavedOnWorldExit = true;
                }
            }

            // 2. Call onUpdate() for all enabled modules (Critical Fix)
            ModuleManager.getInstance().getModules().values().stream()
                .filter(Module::isEnabled)
                .forEach(Module::onUpdate);

            // 3. Save config when exiting the game
            if (mc.currentScreen instanceof GuiMainMenu) {
                if (isGameShuttingDown(mc)) {
                    saveConfigWithCooldown();
                    System.out.println("[Enigmatic] Config saved (exiting game)");
                }
            }

            wasInWorld = isInWorld;
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        // Only trigger if in-game world exists (not in main menu)
        if (mc.theWorld != null && Keyboard.getEventKeyState()) {
            int keyCode = Keyboard.getEventKey();
            
            // Open ClickGUI on Right Shift press
            if (keyCode == Keyboard.KEY_RSHIFT) {
                mc.displayGuiScreen(new ClickguiScreen());
            }
        }
    }

    // ================================== Utility Methods ================================== //

    /**
     * Saves the config with a 60-second cooldown to prevent spamming.
     */
    private static void saveConfigWithCooldown() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSaveTime > 60000) { // 60-second cooldown
            ConfigManager.saveConfig();
            lastSaveTime = currentTime;
        }
    }

    /**
     * Checks if the game is shutting down.
     */
    private static boolean isGameShuttingDown(Minecraft mc) {
        return mc.thePlayer == null && mc.theWorld == null && mc.currentScreen == null;
    }

    /**
     * Handles chat commands.
     */
    public static void handleCommand(String command) {
        String[] args = command.split("\\s+");
        if (args.length < 1) return;

        String baseCmd = args[0].toLowerCase();
        if (!baseCmd.equals(".eni")) return;

        if (args.length == 1) {
            showMessage(UNKNOWN_CMD);
            return;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "help":
                showHelp();
                break;
            case "list":
                listModules();
                break;
            case "enable":
            case "disable":
                handleModuleToggle(args);
                break;
            case "stop":
                shutdownClient();
                break;
            default:
                showMessage(UNKNOWN_CMD);
        }
    }

    // ================================== Helper Methods ================================== //

    private static void showHelp() {
        showMessage(HELP_MSG);
    }

    private static void listModules() {
        StringBuilder sb = new StringBuilder();
        sb.append(MOD_PREFIX).append(WHITE).append("Loaded modules:\n");

        ModuleManager.getInstance().getModules().values().forEach(module -> {
            String status = module.isEnabled()
                ? PURPLE + "ENABLED"
                : GRAY + "DISABLED";
            sb.append(WHITE).append("➤ ")
              .append(YELLOW).append(module.getName())
              .append(WHITE).append(" - Status: ")
              .append(status).append("\n");
        });

        showMessage(sb.toString());
    }

    private static void handleModuleToggle(String[] args) {
        if (args.length < 3) {
            showMessage(MOD_PREFIX + RED + "Usage: .eni " + args[1] + " <module>");
            return;
        }

        String moduleName = args[2];
        ModuleManager moduleManager = ModuleManager.getInstance();

        if (!moduleManager.moduleExists(moduleName)) {
            showMessage(MOD_PREFIX + RED + "Module '" + moduleName + "' not found!");
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("enable");
        moduleManager.getModule(moduleName).ifPresent(module -> {
            if (enable) {
                if (module.isEnabled()) {
                    showMessage(MOD_PREFIX + module.getName() + GRAY + " is already enabled");
                } else {
                    module.setEnabled(true);
                    showMessage(MOD_PREFIX + module.getName() + GREEN + " enabled successfully");
                    ConfigManager.markDirty();
                }
            } else {
                if (!module.isEnabled()) {
                    showMessage(MOD_PREFIX + module.getName() + GRAY + " is already disabled");
                } else {
                    module.setEnabled(false);
                    showMessage(MOD_PREFIX + module.getName() + RED + " disabled successfully");
                    ConfigManager.markDirty();
                }
            }
        });
    }

    /**
     * Shuts down the client forcefully.
     */
    private static void shutdownClient() {
        ConfigManager.saveOnShutdown();
        System.out.println("[Enigmatic] Initiating shutdown...");

        ModuleManager.getInstance().getModules().values().stream()
            .filter(Module::isEnabled)
            .forEach(module -> module.setEnabled(false));

        new Thread(() -> {
            try {
                Minecraft.getMinecraft().shutdown();
                Thread.sleep(1000);
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Displays a message in the chat.
     */
    public static void showMessage(String message) {
        if (Minecraft.getMinecraft().ingameGUI != null) {
            Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(
                new ChatComponentText(message)
            );
        }
    }

    /**
     * Displays an error message in the chat.
     */
    public static void showErrorMessage(String message) {
        showMessage(MOD_PREFIX + RED + "ERROR: " + message);
    }
}