package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.RangeSliderBuilder;
import me.shedaniel.clothconfig2.impl.utils.TimerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;


public class ClothConfigScreenHooks extends ConfigCategoryImpl {
    public static ClothConfigScreenHooks INSTANCE;

    private static final String TARGET_ALL = "All";
    private static final String TARGET_PLAYERS = "Players";
    private static final String TARGET_MOBS = "Mobs";

    private final RangeSliderBuilder delay = new RangeSliderBuilder(
            "Delay", "Ms between hits", 585, 600, 0, 1000, 1);
    private final BooleanToggleBuilder crit = new BooleanToggleBuilder(
            "Only Crit", "Only hit while falling", false);
    private final BooleanToggleBuilder hitWhileEating = new BooleanToggleBuilder(
            "Hit While Eating", "Allow hits while using items", false);
    private final EnumSelectorBuilder target = new EnumSelectorBuilder(
            "Target", "Who to attack", TARGET_ALL, TARGET_ALL, TARGET_PLAYERS, TARGET_MOBS);
    private final BooleanToggleBuilder targetLock = new BooleanToggleBuilder(
            "Target Lock", "Only hit entity you are attacking", false);
    private final BooleanToggleBuilder slotLock = new BooleanToggleBuilder(
            "Slot Lock", "Only on hotbar slot", false);
    private final DoubleFieldBuilder lockedSlot = new DoubleFieldBuilder(
            "Locked Slot", "Hotbar slot 1-9", 1, 1, 9, 1);
    private final BooleanToggleBuilder tpCheck = new BooleanToggleBuilder(
            "Teleport Check", "Turn off TriggerBot temporarily when teleported", false);
    private final DoubleFieldBuilder tpReenable = new DoubleFieldBuilder(
            "Re-enable Delay", "Seconds before turning back on (set to 10s for Never)", 1.0, 1.0, 10.0, 0.5);

    private boolean tempDisabled = false;
    private long tempDisableTime = 0;

    private final TimerUtils hitTimer = new TimerUtils();
    private int currentDelayMs;
    private boolean readyToHit = true;

    public ClothConfigScreenHooks() {
        super("TriggerBot", "Hits for you when your crosshair is on someone", Cat.COMBAT);
        INSTANCE = this;
        addSetting(delay);
        addSetting(target);
        addSetting(crit);
        addSetting(hitWhileEating);
        addSetting(targetLock);
        addSetting(slotLock);
        addSetting(lockedSlot);
        addSetting(tpCheck);
        addSetting(tpReenable);
        
        lockedSlot.setVisibleWhen(slotLock::get);
        tpReenable.setSuffix("s");
        tpReenable.setVisibleWhen(tpCheck::get);
    }

    public boolean isTpCheck() {
        return tpCheck.get();
    }

    public double getTpReenableDelay() {
        return tpReenable.get();
    }

    public void tempDisable(long durationMs) {
        if (isEnabled()) {
            setEnabled(false);
            this.tempDisabled = true;
            this.tempDisableTime = System.currentTimeMillis() + durationMs;
        }
    }

    public void checkTempReenable() {
        if (tempDisabled && System.currentTimeMillis() >= tempDisableTime) {
            tempDisabled = false;
            setEnabled(true);
        }
    }

    public void onFrame(MinecraftClient mc) {
    }

    public void onTick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (shouldDeferToAura(mc)) {
            return;
        }
        if (TriggerCombatChecks.shouldSkipTriggerAttack(mc)) {
            return;
        }
        if (slotLock.get() && mc.player.getInventory().selectedSlot != getLockedSlot()) {
            return;
        }
        if (isAimingOverFriend(mc)) {
            return;
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult entityHit)) {
            return;
        }
        if (mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            return;
        }
        tryHit(mc, entityHit.getEntity());
    }

    public void onPreMotion(MinecraftClient mc) {
    }

    public void onPostMotion(MinecraftClient mc) {
    }

    
    public static boolean shouldCancelDoAttack(MinecraftClient mc) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || mc.player == null || mc.getWindow() == null) {
            return false;
        }
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS;
    }

    private boolean tryHit(MinecraftClient mc, Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (targetLock.get() && mc.player.getAttacking() != null && entity != mc.player.getAttacking()) {
            return false;
        }
        if (!isAllowedTarget(entity)) {
            return false;
        }
        if (entity instanceof PlayerEntity player && isFilteredByAntibot(player)) {
            return false;
        }
        if (entity instanceof PlayerEntity player && isFriend(player)) {
            return false;
        }
        if (crit.get() && mc.player.fallDistance <= 0.0F) {
            return false;
        }
        if (!hitWhileEating.get() && mc.player.isUsingItem()) {
            return false;
        }
        if (!readyToHit && !hitTimer.delay(currentDelayMs)) {
            return false;
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || ehr.getEntity() != entity) {
            return false;
        }
        if (!TriggerCombatChecks.canMeleeHitWithLook(mc, entity)) {
            return false;
        }

        if (mc.interactionManager != null) {
            mc.interactionManager.attackEntity(mc.player, entity);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }

        HitRegistration.onAttack(mc, entity);

        if (entity instanceof PlayerEntity victim) {
            HealthEstimate.onOurAttack(victim);
        }

        MouseSimulation.attackOnce(mc);

        readyToHit = false;
        currentDelayMs = randomMs(delay);
        hitTimer.reset();
        return true;
    }

    private boolean isAllowedTarget(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        String mode = target.get();
        if (TARGET_ALL.equals(mode)) {
            return true;
        }
        if (TARGET_PLAYERS.equals(mode)) {
            return entity instanceof PlayerEntity || entity instanceof ZombieEntity;
        }
        if (TARGET_MOBS.equals(mode)) {
            return entity instanceof MobEntity && !(entity instanceof PlayerEntity);
        }
        return false;
    }

    private int getLockedSlot() {
        return Math.max(0, Math.min(8, (int) lockedSlot.get() - 1));
    }

    private static boolean isFilteredByAntibot(Entity e) {
        if (!(e instanceof PlayerEntity)) {
            return false;
        }
        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        if (mgr == null) {
            return false;
        }
        ListEntryImpl ab = mgr.getModuleByClass(ListEntryImpl.class);
        return ab != null && ab.isEnabled() && ab.isBot(e);
    }

    private static boolean isFriend(PlayerEntity p) {
        return FriendManager.isCombatExempt(p);
    }

    private static boolean isAimingOverFriend(MinecraftClient mc) {
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) {
            return false;
        }
        if (!(ehr.getEntity() instanceof PlayerEntity player)) {
            return false;
        }
        return isFriend(player);
    }

    public static boolean shouldDeferToAura(MinecraftClient mc) {
        return false;
    }

    public boolean isOnlyCrits() {
        return crit.get();
    }

    private static int randomMs(RangeSliderBuilder range) {
        double min = range.getMinVal();
        double max = range.getMaxVal();
        if (max <= min) {
            return (int) min;
        }
        return (int) (min + Math.random() * (max - min));
    }

    @Override
    public void onEnable() {
        readyToHit = true;
        currentDelayMs = randomMs(delay);
        hitTimer.reset();
    }

    @Override
    public void onDisable() {
    }
}
