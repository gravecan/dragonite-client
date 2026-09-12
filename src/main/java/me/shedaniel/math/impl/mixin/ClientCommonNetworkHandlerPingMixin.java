package me.shedaniel.math.impl.mixin;

import me.shedaniel.clothconfig2.impl.Config_ProtocolScan;
import me.shedaniel.clothconfig2.impl.TransactionFingerprintEngine;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerPingMixin {

    @Inject(method = "onPing", at = @At("HEAD"))
    private void cloth$recordTransactionPing(CommonPingS2CPacket packet, CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayNetworkHandler)) {
            return;
        }
        TransactionFingerprintEngine.recordPing(packet.getParameter());
    }
}
