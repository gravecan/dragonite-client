package me.shedaniel.math.impl.mixin;

import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExplosionS2CPacket.class)
public interface ExplosionVelocityAccessor {
    @Accessor("playerVelocityX")
    float getPlayerVelocityX();

    @Accessor("playerVelocityX")
    @Mutable
    void setPlayerVelocityX(float playerVelocityX);

    @Accessor("playerVelocityY")
    float getPlayerVelocityY();

    @Accessor("playerVelocityY")
    @Mutable
    void setPlayerVelocityY(float playerVelocityY);

    @Accessor("playerVelocityZ")
    float getPlayerVelocityZ();

    @Accessor("playerVelocityZ")
    @Mutable
    void setPlayerVelocityZ(float playerVelocityZ);
}
