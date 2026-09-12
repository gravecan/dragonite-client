package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;


public class Config_NoSlowdown extends ConfigCategoryImpl {

    public static Config_NoSlowdown INSTANCE;

    private final BooleanToggleBuilder allowSprint;
    private final BooleanToggleBuilder whileEating;
    private final BooleanToggleBuilder whileDrinking;
    private final BooleanToggleBuilder whileBlocking;
    private final BooleanToggleBuilder whileBow;
    private final BooleanToggleBuilder mainHand;
    private final BooleanToggleBuilder offHand;
    private final BooleanToggleBuilder cobwebs;

    public Config_NoSlowdown() {
        super("No Slowdown", "Removes eating/drinking/blocking slowdown and cobweb slow.", Cat.MOVEMENT);
        INSTANCE = this;

        allowSprint = new BooleanToggleBuilder("Allow Sprint", "Sprint while using items", true);
        whileEating = new BooleanToggleBuilder("While Eating", "Food", true);
        whileDrinking = new BooleanToggleBuilder("While Drinking", "Potions, milk, honey", true);
        whileBlocking = new BooleanToggleBuilder("While Blocking", "Shield", true);
        whileBow = new BooleanToggleBuilder("While Bow", "Bow / crossbow", true);
        mainHand = new BooleanToggleBuilder("Main Hand", "Main-hand use", true);
        offHand = new BooleanToggleBuilder("Off Hand", "Off-hand use", true);
        cobwebs = new BooleanToggleBuilder("Cobwebs", "No slowdown in cobwebs", true);

        addSetting(allowSprint);
        addSetting(whileEating);
        addSetting(whileDrinking);
        addSetting(whileBlocking);
        addSetting(whileBow);
        addSetting(mainHand);
        addSetting(offHand);
        addSetting(cobwebs);
    }

    public boolean isCobwebEnabled() {
        return isEnabled() && cobwebs.get();
    }

    
    public boolean bypassItemSlow(MinecraftClient mc) {
        return isEnabled() && mc.player != null && !mc.player.hasVehicle() && shouldApply(mc);
    }

    public boolean shouldAllowSprintWhileUsing(MinecraftClient mc) {
        return bypassItemSlow(mc) && allowSprint.get();
    }

    
    public void applyAfterItemSlowdown(MinecraftClient mc, Input input) {
        if (!bypassItemSlow(mc) || input == null || mc.options == null) {
            return;
        }

        float sideways = 0f;
        float forward = 0f;

        if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
            sideways = -1f;
        } else if (!mc.options.rightKey.isPressed() && mc.options.leftKey.isPressed()) {
            sideways = 1f;
        }
        if (mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            forward = 1f;
        } else if (!mc.options.forwardKey.isPressed() && mc.options.backKey.isPressed()) {
            forward = -1f;
        }

        input.movementSideways = sideways;
        input.movementForward = forward;
    }

    private boolean shouldApply(MinecraftClient mc) {
        if (!mc.player.isUsingItem()) {
            return false;
        }

        ItemStack active = mc.player.getActiveItem();
        if (active.isEmpty()) {
            return false;
        }

        Hand hand = mc.player.getActiveHand();
        boolean handOk = (offHand.get() && hand == Hand.OFF_HAND)
                || (mainHand.get() && hand == Hand.MAIN_HAND);
        if (!handOk) {
            return false;
        }

        if (whileEating.get() && active.contains(DataComponentTypes.FOOD)) {
            return true;
        }
        if (whileDrinking.get() && isDrink(active)) {
            return true;
        }
        if (whileBlocking.get() && active.getItem() instanceof ShieldItem) {
            return true;
        }
        return whileBow.get() && isBow(active);
    }

    private static boolean isDrink(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof PotionItem
                || item == Items.MILK_BUCKET
                || item == Items.HONEY_BOTTLE
                || item == Items.OMINOUS_BOTTLE;
    }

    private static boolean isBow(ItemStack stack) {
        var item = stack.getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }
}
