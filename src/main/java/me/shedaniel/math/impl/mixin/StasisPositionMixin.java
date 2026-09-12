package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientPlayNetworkHandler.class)
public abstract class StasisPositionMixin {

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), cancellable = true)
    private void cloth$stasisBlockPosition(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (!AuthGate.mixinGate()) {
            return;
        }

        // TriggerBot TP Check
        me.shedaniel.clothconfig2.impl.ClothConfigScreenHooks triggerbot = me.shedaniel.clothconfig2.impl.ClothConfigScreenHooks.INSTANCE;
        if (triggerbot != null && triggerbot.isEnabled() && triggerbot.isTpCheck()) {
            double delay = triggerbot.getTpReenableDelay();
            if (delay < 10.0) {
                triggerbot.tempDisable((long)(delay * 1000.0));
            } else {
                triggerbot.setEnabled(false);
            }
        }

        // AimAssist (Config_FloatList) TP Check
        me.shedaniel.clothconfig2.impl.Config_FloatList aimassist1 = me.shedaniel.clothconfig2.impl.Config_FloatList.INSTANCE;
        if (aimassist1 != null && aimassist1.isEnabled() && aimassist1.isTpCheck()) {
            double delay = aimassist1.getTpReenableDelay();
            if (delay < 10.0) {
                aimassist1.tempDisable((long)(delay * 1000.0));
            } else {
                aimassist1.setEnabled(false);
            }
        }
    }
}
