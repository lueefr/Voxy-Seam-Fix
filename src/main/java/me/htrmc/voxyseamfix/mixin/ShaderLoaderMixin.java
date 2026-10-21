package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.htrmc.voxyseamfix.ShaderPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Voxy resolves every shader through {@code ShaderLoader.parse(String)}, which returns the fully
 * merged GLSL. Patching here means the text this mod edits is exactly what the driver compiles, no
 * matter how the port lays its shader files out.
 */
@Mixin(ShaderLoader.class)
public class ShaderLoaderMixin {
    @Inject(
            method = "parse(Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private static void voxyseamfix$patchShader(String id, CallbackInfoReturnable<String> cir) {
        String source = cir.getReturnValue();
        if (source == null) {
            return;
        }
        String patched = ShaderPatcher.patch(id, source);
        if (!patched.equals(source)) {
            cir.setReturnValue(patched);
        }
    }
}
