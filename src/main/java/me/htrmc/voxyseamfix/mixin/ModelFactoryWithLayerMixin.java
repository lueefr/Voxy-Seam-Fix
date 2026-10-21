package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.htrmc.voxyseamfix.FluidModelMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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
 * This is the signature of upstream voxy 0.2.x, the 1.21.1 NeoForge port of it, and the Fabric voxy
 * jar that Roxy loads on NeoForge.
 *
 * <p>The layer parameter is {@code @Coerce Object} on purpose: its declared type moved between
 * Minecraft versions ({@code RenderType} up to 1.21.x, {@code ChunkSectionLayer} afterwards), and
 * coercing means the handler itself does not have to be renamed for each of them. {@code require = 0}
 * keeps a voxy build with a different signature from turning into a startup crash, and
 * {@code "required": false} in the mixin config covers the case where the target class itself is
 * unrecognisable.
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
                                            net.minecraft.world.level.block.state.BlockState blockState,
                                            ColourDepthTextureData[] textureData,
                                            boolean isShaded,
                                            boolean darkenedTinting,
                                            @Coerce Object renderLayer,
                                            CallbackInfoReturnable<Object> cir) {
        FluidModelMarker.mark(blockState, cir.getReturnValue());
    }
}
