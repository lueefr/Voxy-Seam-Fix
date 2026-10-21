package me.htrmc.voxyseamfix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plain JSON config, written once to {@code config/voxy-seam-fix.json} on first launch.
 * Missing keys fall back to the defaults below, so old config files keep working after updates.
 */
public final class SeamFixConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Master switch. When false the shaders are left completely untouched. */
    public boolean enabled = true;

    /**
     * World Y of the fluid surface the sea is pinned to. {@code -1} means "use the level's sea
     * level" (63 in the overworld), which is what almost everybody wants.
     */
    public int fluidSurfaceY = -1;

    /**
     * Fallback for voxy builds this mod cannot hook: voxy never tells us which LOD models are
     * fluids, so widen the pin to every translucent model instead. That is still correct for water
     * (water is always translucent) and only differs for translucent blocks that happen to sit
     * exactly on the fluid surface. Leave it off unless the log says the fluid hook did not apply
     * and the sea still steps.
     */
    public boolean pinTranslucentModels = false;

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("voxy-seam-fix.json");
    }

    public static SeamFixConfig loadOrCreate() {
        Path path = path();
        try {
            if (Files.exists(path)) {
                SeamFixConfig loaded = GSON.fromJson(Files.readString(path), SeamFixConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Exception e) {
            VoxySeamFix.LOGGER.warn("voxy-seam-fix: could not read {}, falling back to defaults", path, e);
        }

        SeamFixConfig config = new SeamFixConfig();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(config));
        } catch (Exception e) {
            VoxySeamFix.LOGGER.warn("voxy-seam-fix: could not write {}", path, e);
        }
        return config;
    }
}
