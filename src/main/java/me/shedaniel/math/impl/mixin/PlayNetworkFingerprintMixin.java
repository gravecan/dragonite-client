package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_ProtocolScan;
import me.shedaniel.clothconfig2.impl.TransactionFingerprintEngine;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientPlayNetworkHandler.class)
public abstract class PlayNetworkFingerprintMixin {

    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void cloth$beginCapture(GameJoinS2CPacket packet, CallbackInfo ci) {
        TransactionFingerprintEngine.setListening(true);
        TransactionFingerprintEngine.beginCapture();
    }
}
