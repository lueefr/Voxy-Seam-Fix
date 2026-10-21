package me.htrmc.voxyseamfix;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only NeoForge entry point. The mod has no gameplay content: everything happens in the
 * two mixins that (a) flag voxy's fluid LOD models and (b) patch voxy's quad setup shader so those
 * fluids are pinned to one real plane instead of following the LOD grid.
 */
@Mod(value = VoxySeamFix.MOD_ID, dist = Dist.CLIENT)
public final class VoxySeamFix {
    public static final String MOD_ID = "voxy_seam_fix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Byte offset of {@code flagsA} inside a voxy {@code BlockModel}:
     * {@code uint faceData[6]; uint flagsA; uint colourTint; uint customId; uint _pad[7];}.
     */
    public static final long BLOCK_MODEL_FLAGS_OFFSET = 4L * 6;

    /**
     * Bit 4 of {@code flagsA}. voxy only defines bits 0..3 (has tint, biome colour LUT, translucent,
     * shaded), so this one is free in every known build and is what the shader patch tests for.
     */
    public static final int FLUID_MODEL_FLAG = 1 << 4;

    /** Used when no level is loaded yet (e.g. a shader compiled from the main menu). */
    private static final int DEFAULT_SEA_LEVEL = 63;

    private static volatile SeamFixConfig config;

    public VoxySeamFix() {
        config = SeamFixConfig.loadOrCreate();
        LOGGER.info("voxy-seam-fix: loaded (enabled={}, fluidSurfaceY={}, pinTranslucentModels={}, voxy present={})",
                config.enabled, config.fluidSurfaceY, config.pinTranslucentModels, voxyClassesPresent());
        if (!voxyClassesPresent()) {
            LOGGER.warn("voxy-seam-fix: no voxy classes on the classpath, this mod does nothing without voxy");
        }
    }

    public static SeamFixConfig config() {
        SeamFixConfig local = config;
        if (local == null) {
            synchronized (VoxySeamFix.class) {
                local = config;
                if (local == null) {
                    config = local = SeamFixConfig.loadOrCreate();
                }
            }
        }
        return local;
    }

    /**
     * World Y the fluid surface is pinned to, resolved once per shader compile.
     */
    public static int fluidSurfaceLevel() {
        int override = config().fluidSurfaceY;
        if (override >= 0) {
            return override;
        }
        try {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                return level.getSeaLevel();
            }
        } catch (Throwable ignored) {
            // Not on the client thread, or a level without a sea level definition; keep the default.
        }
        return DEFAULT_SEA_LEVEL;
    }

    /** True when voxy's classes are actually there, checked without triggering class init. */
    public static boolean voxyClassesPresent() {
        try {
            Class.forName("me.cortex.voxy.client.core.gl.shader.ShaderLoader", false, VoxySeamFix.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
