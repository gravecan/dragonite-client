package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;


public class Config_AutoTool extends ConfigCategoryImpl {

    public static Config_AutoTool INSTANCE;

    private final BooleanToggleBuilder switchBack;
    private final BooleanToggleBuilder onlyWhenSneak;

    private int lastSlot = -1;

    public Config_AutoTool() {
        super("Auto Tool", "Switch to the best tool for the job", Cat.MISC);
        INSTANCE = this;

        switchBack = new BooleanToggleBuilder("Switch Back", "Restore slot when done", false);
        onlyWhenSneak = new BooleanToggleBuilder("Only When Sneaking", "Only swap while sneaking", false);

        addSetting(switchBack);
        addSetting(onlyWhenSneak);
    }

    public void onAttackEntity(net.minecraft.entity.Entity target) {
        if (!isEnabled() || !(target instanceof PlayerEntity)) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return;
        }
        int best = bestWeaponSlot(mc);
        if (best != mc.player.getInventory().selectedSlot) {
            mc.player.getInventory().selectedSlot = best;
        }
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (!mc.options.attackKey.isPressed()) {
            if (lastSlot >= 0 && lastSlot < 9 && switchBack.get()) {
                mc.player.getInventory().selectedSlot = lastSlot;
                lastSlot = -1;
            }
            return;
        }

        if (onlyWhenSneak.get() && !mc.player.isSneaking()) {
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) {
            return;
        }

        BlockState state = mc.world.getBlockState(bhr.getBlockPos());
        int best = bestToolSlot(mc, state);
        if (best == mc.player.getInventory().selectedSlot) {
            return;
        }
        if (lastSlot < 0) {
            lastSlot = mc.player.getInventory().selectedSlot;
        }
        mc.player.getInventory().selectedSlot = best;
    }

    private static int bestToolSlot(MinecraftClient mc, BlockState state) {
        int best = mc.player.getInventory().selectedSlot;
        float bestSpeed = mc.player.getInventory().getStack(best).getMiningSpeedMultiplier(state);
        for (int slot = 0; slot < 9; slot++) {
            float speed = mc.player.getInventory().getStack(slot).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private int bestWeaponSlot(MinecraftClient mc) {
        int best = mc.player.getInventory().selectedSlot;
        float bestDmg = weaponDamage(mc.player.getInventory().getStack(best));
        for (int slot = 0; slot < 9; slot++) {
            float dmg = weaponDamage(mc.player.getInventory().getStack(slot));
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = slot;
            }
        }
        return best;
    }

    private static float weaponDamage(ItemStack stack) {
        if (stack.getItem() instanceof SwordItem sword) {
            return sword.getMaterial().getAttackDamage();
        }
        if (stack.getItem() instanceof AxeItem axe) {
            return axe.getMaterial().getAttackDamage();
        }
        return 0f;
    }
}
