package me.shedaniel.math.impl.mixin;



import me.shedaniel.clothconfig2.impl.Config_AirStuck;
import me.shedaniel.clothconfig2.impl.Config_BoatFly;
import me.shedaniel.clothconfig2.impl.HandViewHelper;

import me.shedaniel.clothconfig2.impl.Config_Criticals;

import me.shedaniel.clothconfig2.impl.Config_FreeCam;

import me.shedaniel.clothconfig2.impl.Config_NoFall;

import me.shedaniel.clothconfig2.impl.HotbarSlotSync;

import me.shedaniel.clothconfig2.impl.MovementPacketHooks;

import me.shedaniel.clothconfig2.internal.AuthGate;
import me.shedaniel.clothconfig2.internal.MixinRuntimeProbe;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkState;

import net.minecraft.network.packet.Packet;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerSessionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.ModifyVariable;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(ClientConnection.class)

public abstract class ClientConnectionSendMixin {

    /*
     * ClientPlayNetworkHandler can publish its chat session while it is being
     * constructed. During the configuration -> play hand-off the inbound
     * listener is changed before the outbound encoder. A quickly completed
     * profile-key future can therefore attempt to encode this play-only packet
     * with the configuration codec. Keep that single packet until the vanilla
     * outbound transition has completed.
     */
    @Unique
    private Packet<?> cloth$deferredPlayerSession;

    @ModifyVariable(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> cloth$sanitizeOutboundMovement(Packet<?> packet) {
        if (!AuthGate.mixinGate()) {
            return packet;
        }
        packet = Config_BoatFly.sanitizeOutboundPacket(packet);
        if (MovementPacketHooks.isNoFallActive()) {
            packet = Config_NoFall.INSTANCE.handleOutbound(packet);
        }
        return packet;
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)

    private void onSend(Packet<?> packet, CallbackInfo ci) {
        MixinRuntimeProbe.noteMixinApplied("ClientConnectionSendMixin");

        ClientConnection connection = (ClientConnection) (Object) this;
        if (packet instanceof PlayerSessionC2SPacket
                && !(connection.getPacketListener() instanceof ClientPlayNetworkHandler)) {
            cloth$deferredPlayerSession = packet;
            ci.cancel();
            return;
        }

        if (AuthGate.mixinGate() && packet instanceof HandSwingC2SPacket) {

            HandViewHelper.onOutboundHandSwing();

        }

        if (AuthGate.mixinGate() && packet instanceof UpdateSelectedSlotC2SPacket slotPacket) {
            if (HotbarSlotSync.shouldBlockResyncPacket(slotPacket.getSelectedSlot())) {
                ci.cancel();
                return;
            }
            HotbarSlotSync.noteSent(slotPacket.getSelectedSlot());
        }

        if (AuthGate.mixinGate()
                && Config_Criticals.INSTANCE != null
                && Config_Criticals.INSTANCE.handleOutbound(packet, ci)) {
            ci.cancel();
            return;
        }

        if (!AuthGate.mixinGate() || !MovementPacketHooks.shouldIntercept(packet)) {

            return;

        }

        if (MovementPacketHooks.isFreeCamActive()

                && !Config_FreeCam.isAnchorSend()

                && Config_FreeCam.INSTANCE.handleOutbound(packet)) {

            ci.cancel();

            return;

        }

        if (MovementPacketHooks.isAirStuckActive()

                && !Config_AirStuck.isAnchorSend()

                && Config_AirStuck.INSTANCE.handleOutbound(packet)) {

            ci.cancel();

        }

    }

    @Inject(method = "transitionOutbound", at = @At("TAIL"))
    private void cloth$flushDeferredPlayerSession(NetworkState<?> state, CallbackInfo ci) {
        if (state.id() != NetworkPhase.PLAY || cloth$deferredPlayerSession == null) {
            return;
        }
        Packet<?> packet = cloth$deferredPlayerSession;
        cloth$deferredPlayerSession = null;
        ((ClientConnection) (Object) this).send(packet);
    }

}


