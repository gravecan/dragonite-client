package me.shedaniel.clothconfig2.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;


public final class PreMovementMelee {
    private PreMovementMelee() {}

    public static void attack(MinecraftClient mc, LivingEntity target, boolean swing) {
        attack(mc, target, swing, -1, -1);
    }

    
    public static void attack(MinecraftClient mc, LivingEntity target, boolean swing, int slotBefore, int slotAfter) {
        attack(mc, target, swing, slotBefore, slotAfter, false);
    }

    
    public static void attackFriendSync(MinecraftClient mc, LivingEntity target, boolean sprintReset) {
        if (mc.player == null || target == null) {
            return;
        }
        boolean sprintHandled = CombatMovementSync.prepareFriendSprintReset(mc, sprintReset);
        attack(mc, target, true, -1, -1, sprintHandled);
    }

    
    public static void attackGrimSync(MinecraftClient mc, LivingEntity target, boolean sprintReset) {
        if (mc.player == null || target == null) {
            return;
        }
        CombatMovementSync.prepareMeleeAttack(mc, sprintReset);
        attack(mc, target, true, -1, -1, true);
        CombatMovementSync.onAuraMeleeHit(mc);
    }

    
    public static void attack(MinecraftClient mc, LivingEntity target, boolean swing,
                            int slotBefore, int slotAfter, boolean sprintAlreadyHandled) {
        if (mc.player == null || mc.getNetworkHandler() == null || target == null) {
            return;
        }

        if (slotBefore >= 0 && slotBefore <= 8) {
            HotbarSlotSync.sendIfChanged(mc, slotBefore);
        }

        if (!sprintAlreadyHandled && mc.player.isOnGround() && mc.player.isSprinting()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }

        if (swing) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        if (swing) {
            mc.player.swingHand(Hand.MAIN_HAND);
            Config_Hitsound hitsound = Config_Hitsound.INSTANCE;
            if (hitsound != null) {
                hitsound.setLastAttacked(target);
                hitsound.playHitSound();
            }
        }
        mc.player.resetLastAttackedTicks();

        if (slotAfter >= 0 && slotAfter <= 8) {
            HotbarSlotSync.sendIfChanged(mc, slotAfter);
        }
    }
}
