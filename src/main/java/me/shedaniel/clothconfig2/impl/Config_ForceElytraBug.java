package me.shedaniel.clothconfig2.impl;



import net.minecraft.client.MinecraftClient;

import net.minecraft.entity.EquipmentSlot;

import net.minecraft.item.ItemStack;

import net.minecraft.item.Items;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;





public class Config_ForceElytraBug extends ConfigCategoryImpl {



    public static Config_ForceElytraBug INSTANCE;



    private boolean sentThisFlight;



    public Config_ForceElytraBug() {

        super("Force Elytra Bug", "Force elytra glide while airborne", Cat.MOVEMENT);

        INSTANCE = this;

        setTooltip("Sends elytra glide packet once per jump when wearing elytra (AutoWalk-style timing).");

    }



    @Override

    public void onEnable() {

        sentThisFlight = false;

    }



    

    public static void applyGlide(MinecraftClient mc) {

        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.player == null || mc.getNetworkHandler() == null) {

            return;

        }

        if (mc.player.getAbilities().flying || mc.player.hasVehicle() || mc.player.isClimbing()) {

            return;

        }



        if (mc.player.checkFallFlying()) {
            mc.getNetworkHandler().sendPacket(
                    new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            INSTANCE.sentThisFlight = true;
        }

    }



    public void tick(MinecraftClient mc) {

        

    }

}


