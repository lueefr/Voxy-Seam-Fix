package me.htrmc.voxyseamfix.mixin;

import me.cortex.voxy.common.util.MemoryBuffer;
import me.htrmc.voxyseamfix.ModelBufferAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the native address of the model buffer voxy just filled in, so
 * {@link me.htrmc.voxyseamfix.FluidModelMarker} can set the fluid flag before the buffer is
 * uploaded to the GPU.
 *
 * <p>The target is a name string because the class is {@code private static final} inside
 * {@code ModelFactory}. Only the {@code MemoryBuffer} type is referenced, and that one comes from
 * the compile-only stubs in {@code src/voxyStubs}.
 */
@Mixin(targets = "me.cortex.voxy.client.core.model.ModelFactory$ModelBakeResultUpload")
public abstract class ModelBakeResultUploadMixin implements ModelBufferAccess {
    @Shadow
    @Final
    private MemoryBuffer model;

    @Override
    public long voxyseamfix$modelAddress() {
        return this.model.address;
    }
}
