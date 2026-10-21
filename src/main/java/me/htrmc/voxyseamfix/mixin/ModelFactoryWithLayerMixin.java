package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.htrmc.voxyseamfix.FluidModelMarker;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flags the LOD models voxy bakes for fluids, for voxy builds that pass the render layer in:
 *
 * <pre>
 * ModelBakeResultUpload processTextureBakeResult(int, BlockState, ColourDepthTextureData[],
 *                                                boolean, boolean, RenderType)
 * </pre>
 *
 * This is the signature of upstream voxy 0.2.x on 1.21.1, the NeoForge port of it, and the Fabric
 * voxy jar that Roxy loads on NeoForge.
 *
 * <p>The layer parameter is spelled {@code RenderType} rather than {@code @Coerce}d to
 * {@code Object}: Mixin's {@code @Coerce} only exists for primitive widening, so an object argument
 * cannot be coerced and the injector would refuse to apply. Voxy builds on a Minecraft version where
 * that parameter became {@code ChunkSectionLayer} simply do not match this selector, which is why
 * {@code require = 0} is here and {@code "required": false} is set in the mixin config: a different
 * signature must never turn into a startup crash.
 */
@Mixin(ModelFactory.class)
public abstract class ModelFactoryWithLayerMixin {
    @Inject(
            method = "processTextureBakeResult(ILnet/minecraft/world/level/block/state/BlockState;"
                    + "[Lme/cortex/voxy/client/core/model/ColourDepthTextureData;ZZ"
                    + "Lnet/minecraft/client/renderer/RenderType;)"
                    + "Lme/cortex/voxy/client/core/model/ModelFactory$ModelBakeResultUpload;",
            at = @At("RETURN"),
            require = 0
    )
    private void voxyseamfix$markFluidModel(int blockId,
                                            BlockState blockState,
                                            ColourDepthTextureData[] textureData,
                                            boolean isShaded,
                                            boolean darkenedTinting,
                                            RenderType renderLayer,
                                            CallbackInfoReturnable<Object> cir) {
        FluidModelMarker.mark(blockState, cir.getReturnValue());
    }
}
