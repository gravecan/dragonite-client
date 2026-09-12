package me.shedaniel.math.impl.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapTextureManager.class)
public interface LightmapTextureManagerAccessor {
    @Accessor("image")
    NativeImage cloth$getImage();

    @Accessor("texture")
    NativeImageBackedTexture cloth$getTexture();

    @Accessor("dirty")
    void cloth$setDirty(boolean dirty);
}
