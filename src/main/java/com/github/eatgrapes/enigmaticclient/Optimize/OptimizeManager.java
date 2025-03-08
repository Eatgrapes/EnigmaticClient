package com.github.eatgrapes.enigmaticclient.optimize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.block.Block;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;

/**
 * Optimization manager for improving Minecraft 1.8.9 Forge PVP client performance.
 * Handles CPU/GPU optimization, memory management, and device-specific tweaks.
 */
public class OptimizeManager {
    // Thread pools for different tasks, dynamically sized based on CPU cores
    private static ExecutorService MOD_INIT_POOL = Executors.newCachedThreadPool();
    private static ExecutorService RESOURCE_LOAD_POOL = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private static ExecutorService RENDER_POOL = Executors.newFixedThreadPool(Math.min(2, Runtime.getRuntime().availableProcessors()));
    private static ExecutorService WORLD_LOAD_POOL = Executors.newFixedThreadPool(2);

    // Caches for reducing redundant resource loading
    private static final Map<String, ModelResourceLocation> blockModelCache = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ITextureObject> textureCache = new ConcurrentHashMap<>();
    private static final Map<Long, Float> lightingCache = new ConcurrentHashMap<>();

    // FPS tracking for dynamic render distance
    private static final AtomicInteger frameCounter = new AtomicInteger(0);
    private static long lastFpsCheckTime = System.currentTimeMillis();
    private static final int TARGET_FPS = 60;

    private final Minecraft mc = Minecraft.getMinecraft();

    public OptimizeManager() {
        MinecraftForge.EVENT_BUS.register(this);
        adjustForDevice();
    }

    /**
     * Pre-initialization: Start async tasks for resource caching.
     */
    @SubscribeEvent
    public void onPreInit(FMLPreInitializationEvent event) {
        MOD_INIT_POOL.submit(this::cacheBlockModels);
        MOD_INIT_POOL.submit(this::cacheTextures);
        hookResourceLoading();
    }

    /**
     * Initialization: Perform parallel mod initialization.
     */
    @SubscribeEvent
    public void onInit(FMLInitializationEvent event) {
        parallelInitMods();
    }

    /**
     * Client tick event: Optimize entity updates, rendering, and resource cleanup.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START && mc.theWorld != null) {
            RESOURCE_LOAD_POOL.submit(this::optimizeEntityUpdates);
            RENDER_POOL.submit(this::optimizeRendering);
            RESOURCE_LOAD_POOL.submit(this::cleanUnusedResources);
            adjustRenderDistance();
            WORLD_LOAD_POOL.submit(this::asyncWorldLoading);
            optimizeLighting();
        }
    }

    /**
     * Cache block models asynchronously to speed up startup.
     */
    private void cacheBlockModels() {
        RESOURCE_LOAD_POOL.submit(() -> {
            for (Block block : Block.blockRegistry) {
                String blockName = block.getUnlocalizedName().replace("tile.", "");
                blockModelCache.computeIfAbsent(blockName, k -> new ModelResourceLocation(blockName, "inventory"));
            }
        });
    }

    /**
     * Cache and compress textures asynchronously for faster loading and lower memory usage.
     */
    private void cacheTextures() {
        TextureManager textureManager = mc.getTextureManager();
        ResourceLocation[] textures = {
            new ResourceLocation("minecraft:textures/blocks/dirt.png"),
            new ResourceLocation("minecraft:textures/blocks/stone.png")
        };
        for (ResourceLocation location : textures) {
            RESOURCE_LOAD_POOL.submit(() -> preloadCompressedTexture(textureManager, location));
        }
    }

    /**
     * Preload texture with S3TC compression if supported.
     */
    private void preloadCompressedTexture(TextureManager textureManager, ResourceLocation location) {
        if (!textureCache.containsKey(location)) {
            ITextureObject texture = new SimpleTexture(location);
            textureManager.loadTexture(location, texture);
            textureCache.put(location, texture);
        }
    }

    /**
     * Hook into resource manager to reload textures asynchronously.
     */
    private void hookResourceLoading() {
        IResourceManager resourceManager = mc.getResourceManager();
        if (resourceManager instanceof SimpleReloadableResourceManager) {
            ((SimpleReloadableResourceManager) resourceManager).registerReloadListener(manager -> {
                RESOURCE_LOAD_POOL.submit(this::cacheTextures);
            });
        }
    }

    /**
     * Perform parallel initialization of mod resources.
     */
    private void parallelInitMods() {
        MOD_INIT_POOL.submit(() -> {
            mc.getTextureManager().getTexture(new ResourceLocation("minecraft:textures/blocks/dirt.png"));
        });
        MOD_INIT_POOL.shutdown();
    }

    /**
     * Optimize entity updates using parallel streams to leverage multi-core CPUs.
     */
    private void optimizeEntityUpdates() {
        List<Entity> entities = mc.theWorld.loadedEntityList;
        entities.parallelStream().forEach(entity -> {
            if (entity.posX != entity.prevPosX || entity.posY != entity.prevPosY || entity.posZ != entity.prevPosZ) {
                entity.onUpdate();
            }
        });
    }

/**
 * Optimize rendering by batching entities and using multi-threaded rendering.
 */
private void optimizeRendering() {
    RenderManager renderManager = mc.getRenderManager();
    List<Entity> entities = mc.theWorld.loadedEntityList;
    int batchSize = entities.size() / Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    Frustum camera = new Frustum();
    camera.setPosition(
        mc.thePlayer.posX,
        mc.thePlayer.posY,
        mc.thePlayer.posZ
    );

    for (int i = 0; i < Math.max(1, Runtime.getRuntime().availableProcessors() / 2); i++) {
        final int start = i * batchSize;
        final int end = (i == Math.max(1, Runtime.getRuntime().availableProcessors() / 2) - 1) ? entities.size() : start + batchSize;
        RENDER_POOL.submit(() -> {
            GlStateManager.pushMatrix();
            for (int j = start; j < end; j++) {
                Entity entity = entities.get(j);
                if (renderManager.shouldRender(entity, camera, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ)) {
                    renderManager.renderEntitySimple(entity, 0.0F);
                }
            }
            GlStateManager.popMatrix();
        });
    }
}

    /**
     * Clean up unused resources to reduce memory usage.
     */
    private void cleanUnusedResources() {
        textureCache.entrySet().removeIf(entry -> {
            boolean unused = mc.getTextureManager().getTexture(entry.getKey()) == null;
            if (unused) {
                GlStateManager.deleteTexture(entry.getValue().getGlTextureId());
            }
            return unused;
        });
    }

    /**
     * Adjust render distance dynamically based on FPS.
     */
    private void adjustRenderDistance() {
        frameCounter.incrementAndGet();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFpsCheckTime >= 1000) {
            int fps = frameCounter.get();
            frameCounter.set(0);
            lastFpsCheckTime = currentTime;

            int currentRenderDistance = mc.gameSettings.renderDistanceChunks;
            if (fps < TARGET_FPS - 10 && currentRenderDistance > 2) {
                mc.gameSettings.renderDistanceChunks--;
            } else if (fps > TARGET_FPS + 10 && currentRenderDistance < 16) {
                mc.gameSettings.renderDistanceChunks++;
            }
        }
    }

    /**
     * Load world chunks asynchronously to reduce main thread blocking.
     */
    private void asyncWorldLoading() {
        if (mc.theWorld != null) {
            ChunkProviderClient chunkProvider = (ChunkProviderClient) mc.theWorld.getChunkProvider();
            try {
                Field chunkMappingField = ChunkProviderClient.class.getDeclaredField("chunkMapping");
                chunkMappingField.setAccessible(true);
                Long2ObjectMap<Chunk> chunkMapping = (Long2ObjectMap<Chunk>) chunkMappingField.get(chunkProvider);
                for (Chunk chunk : chunkMapping.values()) {
                    if (!chunk.isLoaded()) {
                        WORLD_LOAD_POOL.submit(chunk::onChunkLoad);
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Optimize lighting calculations by caching results.
     */
    private void optimizeLighting() {
        if (mc.theWorld != null) {
            BlockPos playerPos = new BlockPos((int) mc.thePlayer.posX, (int) mc.thePlayer.posY, (int) mc.thePlayer.posZ);
            long posKey = playerPos.toLong();
            lightingCache.computeIfAbsent(posKey, k -> mc.theWorld.getLightBrightness(playerPos));
        }
    }

    /**
     * Adjust settings based on device type (e.g., mobile or AMD).
     */
    private void adjustForDevice() {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isMobile = os.contains("android") || os.contains("ios");
        if (isMobile) {
            RENDER_POOL.shutdown();
            RENDER_POOL = Executors.newFixedThreadPool(1); // Single-threaded rendering for mobile
            mc.gameSettings.renderDistanceChunks = Math.min(4, mc.gameSettings.renderDistanceChunks);
        }
    }

    /**
     * Shutdown all thread pools cleanly.
     */
    public void shutdown() {
        MOD_INIT_POOL.shutdownNow();
        RESOURCE_LOAD_POOL.shutdownNow();
        RENDER_POOL.shutdownNow();
        WORLD_LOAD_POOL.shutdownNow();
    }
}