package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.client.core.model.ColourDepthTextureData;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.htrmc.voxyseamfix.FluidModelMarker;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same job as {@link ModelFactoryWithLayerMixin}, for voxy builds that do not pass the render layer:
 *
 * <pre>
 * ModelBakeResultUpload processTextureBakeResult(int, BlockState, ColourDepthTextureData[],
 *                                                boolean, boolean)
 * </pre>
 *
 * That is the signature of e.g. falling-colud/voxy-forged. Only one of the two mixins can match a
 * given voxy jar (a method cannot have both descriptors), and both are {@code require = 0}, so the
 * unused one is silently skipped.
 */
@Mixin(ModelFactory.class)
public abstract class ModelFactoryNoLayerMixin {
    @Inject(
            method = "processTextureBakeResult(ILnet/minecraft/world/level/block/state/BlockState;"
                    + "[Lme/cortex/voxy/client/core/model/ColourDepthTextureData;ZZ)"
                    + "Lme/cortex/voxy/client/core/model/ModelFactory$ModelBakeResultUpload;",
            at = @At("RETURN"),
            require = 0
    )
    private void voxyseamfix$markFluidModel(int blockId,
                                            BlockState blockState,
                                            ColourDepthTextureData[] textureData,
                                            boolean isShaded,
                                            boolean darkenedTinting,
                                            CallbackInfoReturnable<Object> cir) {
        FluidModelMarker.mark(blockState, cir.getReturnValue());
    }
}
