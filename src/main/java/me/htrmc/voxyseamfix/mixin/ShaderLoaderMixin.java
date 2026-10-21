package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.htrmc.voxyseamfix.ShaderPatcher;
import me.htrmc.voxyseamfix.VoxySeamFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voxy resolves every shader through {@code ShaderLoader.parse(String)}, which returns the fully
 * merged GLSL. Patching here means the text this mod edits is exactly what the driver compiles, no
 * matter how a port lays its shader files out.
 */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
    /** One warning per shader, not one per frame-pipeline rebuild. */
    private static final Set<String> WARNED_IDS = ConcurrentHashMap.newKeySet();

    @Inject(
            method = "parse(Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void voxyseamfix$patchShader(String id, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        if (source == null || !VoxySeamFix.config().enabled) {
            return;
        }

        ShaderPatcher.Result result = ShaderPatcher.patch(source,
                VoxySeamFix.fluidSurfaceLevel(),
                VoxySeamFix.config().pinTranslucentModels);

        switch (result.status()) {
            case PATCHED -> {
                cir.setReturnValue(result.source());
                VoxySeamFix.LOGGER.info("voxy-seam-fix: pinned fluid surfaces to y={} in {}",
                        VoxySeamFix.fluidSurfaceLevel(), id);
            }
            case ANCHORS_MISSING -> {
                if (WARNED_IDS.add(id)) {
                    VoxySeamFix.LOGGER.warn("voxy-seam-fix: voxy's setupQuad in {} no longer matches the anchors this"
                            + " build knows, so fluid surfaces are NOT being pinned. This build of voxy-seam-fix is out"
                            + " of date for this voxy version.", id);
                }
            }
            default -> {
                // Nothing to do for this source.
            }
        }
    }
}
