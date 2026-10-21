package me.htrmc.voxyseamfix;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sets {@link VoxySeamFix#FLUID_MODEL_FLAG} on the LOD model voxy just baked, so the shader patch
 * can tell water and lava apart from every other block that happens to sit on the fluid surface.
 *
 * <p>This is the only place that needs to know anything about voxy's internals, and it is reached
 * through two mixins that target the two {@code processTextureBakeResult} signatures seen in the
 * wild (5 and 6 parameters). Both fail soft: if a voxy build has a signature neither of them
 * matches, nothing is flagged, the shader simply never pins anything and the game keeps working.
 */
public final class FluidModelMarker {
    private FluidModelMarker() {}

    private static final AtomicBoolean FIRST_MARK_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean ADDRESS_FAILURE_LOGGED = new AtomicBoolean();

    /**
     * @param state  the block state that was baked
     * @param result the value voxy's {@code processTextureBakeResult} returned, may be {@code null}
     *               for a deduplicated bake (the shared model is already flagged by then)
     */
    public static void mark(BlockState state, Object result) {
        if (state == null || result == null) {
            return;
        }
        if (!(state.getBlock() instanceof LiquidBlock)) {
            return;
        }
        if (!(result instanceof ModelBufferAccess access)) {
            if (ADDRESS_FAILURE_LOGGED.compareAndSet(false, true)) {
                VoxySeamFix.LOGGER.warn("voxy-seam-fix: voxy's bake result does not expose its model buffer"
                        + " ({}), fluid surfaces are NOT being pinned. This build of voxy-seam-fix does not"
                        + " match this voxy version.", result.getClass().getName());
            }
            return;
        }

        long address = access.voxyseamfix$modelAddress();
        if (address == 0L) {
            return;
        }
        long flags = address + VoxySeamFix.BLOCK_MODEL_FLAGS_OFFSET;
        int current = MemoryUtil.memGetInt(flags);
        if ((current & VoxySeamFix.FLUID_MODEL_FLAG) != 0) {
            return;
        }
        MemoryUtil.memPutInt(flags, current | VoxySeamFix.FLUID_MODEL_FLAG);
        if (FIRST_MARK_LOGGED.compareAndSet(false, true)) {
            VoxySeamFix.LOGGER.info("voxy-seam-fix: marked {} as a fluid LOD model, the voxy hook is live", state);
        }
    }
}
