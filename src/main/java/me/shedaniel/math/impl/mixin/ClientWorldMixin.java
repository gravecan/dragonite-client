package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_Hitsound;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(
        method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZJ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cloth$onPlaySound(
        double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean useDistance, long seed, CallbackInfo ci
    ) {
        Config_Hitsound hitsound = Config_Hitsound.INSTANCE;
        if (hitsound != null && hitsound.isEnabled() && hitsound.replaceCrit.get() && isAttackSound(sound)) {
            ci.cancel();
        }
    }

    private static boolean isAttackSound(SoundEvent sound) {
        return sound == SoundEvents.ENTITY_PLAYER_ATTACK_CRIT
            || sound == SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK
            || sound == SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE
            || sound == SoundEvents.ENTITY_PLAYER_ATTACK_STRONG
            || sound == SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP
            || sound == SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
    }
}
