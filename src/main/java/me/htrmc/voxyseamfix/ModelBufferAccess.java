package me.htrmc.voxyseamfix;

/**
 * Implemented on voxy's bake result by {@link me.htrmc.voxyseamfix.mixin.ModelBakeResultUploadMixin}
 * so this mod can reach the model buffer voxy just wrote.
 */
public interface ModelBufferAccess {
    /** Native address of the (not yet uploaded) voxy {@code BlockModel} buffer. */
    long voxyseamfix$modelAddress();
}
