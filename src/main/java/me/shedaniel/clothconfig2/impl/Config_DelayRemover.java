package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.math.impl.mixin.MinecraftClientAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import java.util.Random;

public class Config_DelayRemover extends ConfigCategoryImpl {
    public static Config_DelayRemover INSTANCE;

    private final BooleanToggleBuilder jumpDelay = new BooleanToggleBuilder("Jump Delay", "Remove jump cooldown for fast bridging", false);
    private final BooleanToggleBuilder placeDelay = new BooleanToggleBuilder("Place Delay", "Hold use key to spam-place blocks/items", false);
    private final DoubleFieldBuilder minDelay = new DoubleFieldBuilder("Min Place Delay", "Min ms between placements", 25, 0, 250, 1);
    private final DoubleFieldBuilder maxDelay = new DoubleFieldBuilder("Max Place Delay", "Max ms between placements", 75, 0, 250, 1);
    private final BooleanToggleBuilder missDelay = new BooleanToggleBuilder("Miss Delay", "Removes miss penalty when swinging in the air", false);
    private final BooleanToggleBuilder allItems = new BooleanToggleBuilder("All Items (Miss)", "Work with any held item for Miss Delay", false);

    private final Random random = new Random();
    private long placeTimer = 0;

    public Config_DelayRemover() {
        super("Delay Remover", "Allows you to bypass various Minecraft action delays", Cat.MISC);
        INSTANCE = this;
        addSetting(jumpDelay);
        addSetting(placeDelay);
        addSetting(minDelay);
        addSetting(maxDelay);
        addSetting(missDelay);
        addSetting(allItems);

        minDelay.setVisibleWhen(placeDelay::get);
        maxDelay.setVisibleWhen(placeDelay::get);
        allItems.setVisibleWhen(missDelay::get);
    }

    public boolean shouldRemoveJumpDelay() {
        return isEnabled() && jumpDelay.get();
    }

    public boolean shouldBlockMissDelay(MinecraftClient mc) {
        if (!isEnabled() || !missDelay.get() || mc.player == null || mc.crosshairTarget == null) {
            return false;
        }
        if (mc.crosshairTarget.getType() != HitResult.Type.MISS) {
            return false;
        }
        if (allItems.get()) {
            return true;
        }
        var stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || !placeDelay.get() || mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (!mc.options.useKey.isPressed()) {
            return;
        }

        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        accessor.setItemUseCooldown(0);

        if (System.currentTimeMillis() < placeTimer) {
            return;
        }
        if (!canPlace(mc)) {
            placeTimer = System.currentTimeMillis() + nextDelay();
            return;
        }
        placeTimer = System.currentTimeMillis() + nextDelay();
        accessor.invokeDoItemUse();
    }

    private int nextDelay() {
        int min = (int) minDelay.get();
        int max = (int) Math.max(min, maxDelay.get());
        return min + random.nextInt(max - min + 1);
    }

    private boolean canPlace(MinecraftClient mc) {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        Item mainItem = main.getItem();
        Item offItem = off.getItem();

        if (main.isOf(Items.RESPAWN_ANCHOR) || main.isOf(Items.GLOWSTONE)
                || off.isOf(Items.RESPAWN_ANCHOR) || off.isOf(Items.GLOWSTONE)) {
            return false;
        }
        if (main.get(DataComponentTypes.FOOD) != null) {
            return false;
        }
        if (off.get(DataComponentTypes.FOOD) != null && !(mainItem instanceof BlockItem)) {
            return false;
        }
        if (mainItem instanceof RangedWeaponItem || offItem instanceof RangedWeaponItem) {
            return false;
        }

        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() == HitResult.Type.MISS) {
            return false;
        }
        if (mc.crosshairTarget instanceof BlockHitResult bhr
                && mc.world.getBlockState(bhr.getBlockPos()).isOf(Blocks.FIRE)) {
            return false;
        }
        return true;
    }
}
