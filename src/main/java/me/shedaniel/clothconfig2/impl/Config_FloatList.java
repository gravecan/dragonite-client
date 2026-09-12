package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.AxeItem;


public class Config_FloatList extends ConfigCategoryImpl implements NeuroAimEngine.Settings {

    public static Config_FloatList INSTANCE;

    private final EnumSelectorBuilder mode;
    private final BooleanToggleBuilder aimThroughWalls;

    private final BooleanToggleBuilder focusOnEnemy;
    private final BooleanToggleBuilder slotLock;
    private final DoubleFieldBuilder slotField;
    private final DoubleFieldBuilder speed;
    private final DoubleFieldBuilder acceleration;
    private final DoubleFieldBuilder range;
    private final DoubleFieldBuilder fov;
    private final DoubleFieldBuilder aimHeight;
    private final BooleanToggleBuilder multipoint;
    private final BooleanToggleBuilder elytraTracking;
    
    private final BooleanToggleBuilder playersOnly = new BooleanToggleBuilder("Players Only", "Target players only", true);
    private final BooleanToggleBuilder teammates = new BooleanToggleBuilder("Teammates", "Target teammates", true);
    private final BooleanToggleBuilder priority = new BooleanToggleBuilder("Priority", "Target priority team first", false);
    private final BooleanToggleBuilder wallsCheck = new BooleanToggleBuilder("Walls Check", "Target visible entities only", true);

    private final BooleanToggleBuilder tpCheck = new BooleanToggleBuilder("Teleport Check", "Turn off AimAssist temporarily when teleported", false);
    private final DoubleFieldBuilder tpReenable = new DoubleFieldBuilder(
            "Re-enable Delay", "Seconds before turning back on (set to 10s for Never)", 1.0, 1.0, 10.0, 0.5);

    private boolean tempDisabled = false;
    private long tempDisableTime = 0;

    private final NeuroAimEngine engine = new NeuroAimEngine();
    private long interactionPauseUntil;

    public Config_FloatList() {
        super("AimAssist", "Pulls your crosshair toward enemies in your FOV.", Cat.COMBAT);
        INSTANCE = this;

        mode = new EnumSelectorBuilder(
                "Mode",
                "Smooth = human track | Blatant = Goon-style | Screenshare = Legit look",
                NeuroAimEngine.MODE_SMOOTH,
                NeuroAimEngine.MODE_SMOOTH,
                NeuroAimEngine.MODE_BLATANT,
                NeuroAimEngine.MODE_SCREENSHARE
        );
        speed = new DoubleFieldBuilder("Speed", "Assist strength", 5.5, 1.0, 10.0, 0.5);
        acceleration = new DoubleFieldBuilder("Acceleration", "Aim speed: low = very slow, high = very fast", 0.35, 0.01, 1.0, 0.01);
        range = new DoubleFieldBuilder("Range", "Max target distance", 20.0, 5.0, 35.0, 1.0);
        fov = new DoubleFieldBuilder("FOV", "Detection cone (degrees)", 100.0, 15.0, 360.0, 5.0);
        aimHeight = new DoubleFieldBuilder("Aim Height", "Fine-tune aim point", 0.55, 0.0, 1.0, 0.05);
        multipoint = new BooleanToggleBuilder("Multipoint", "Blatant: tiny hitbox drift", false);
        elytraTracking = new BooleanToggleBuilder(
                "Elytra Tracking",
                "HVH elytra: lead point, upper hitbox, ping prediction, faster track",
                true
            );
        aimThroughWalls = new BooleanToggleBuilder("Through Walls", "Target without line of sight", false);

        focusOnEnemy = new BooleanToggleBuilder("Target Lock", "On = stick to target. Off = every tick picks closest valid player", false);
        slotLock = new BooleanToggleBuilder("Slot Lock", "Only on hotbar slot", false);
        slotField = new DoubleFieldBuilder("Locked Slot", "Hotbar slot 1-9", 1, 1, 9, 1);

        addSetting(mode);
        addSetting(speed);
        addSetting(acceleration);
        addSetting(range);
        addSetting(fov);
        addSetting(aimHeight);
        addSetting(multipoint);
        addSetting(elytraTracking);
        addSetting(aimThroughWalls);
        addSetting(playersOnly);
        addSetting(teammates);
        addSetting(priority);
        addSetting(wallsCheck);

        addSetting(focusOnEnemy);
        addSetting(slotLock);
        addSetting(slotField);
        addSetting(tpCheck);
        addSetting(tpReenable);

        slotField.setVisibleWhen(() -> slotLock.get() && isNotScreenshareMode());
        tpReenable.setSuffix("s");
        tpReenable.setVisibleWhen(() -> tpCheck.get() && isNotScreenshareMode());

        acceleration.setVisibleWhen(this::isNotScreenshareMode);
        aimHeight.setVisibleWhen(this::isNotScreenshareMode);
        elytraTracking.setVisibleWhen(this::isNotScreenshareMode);
        aimThroughWalls.setVisibleWhen(this::isNotScreenshareMode);
        focusOnEnemy.setVisibleWhen(this::isNotScreenshareMode);
        slotLock.setVisibleWhen(this::isNotScreenshareMode);
        tpCheck.setVisibleWhen(this::isNotScreenshareMode);

        playersOnly.setVisibleWhen(this::isScreenshareMode);
        teammates.setVisibleWhen(this::isScreenshareMode);
        priority.setVisibleWhen(this::isScreenshareMode);
        wallsCheck.setVisibleWhen(this::isScreenshareMode);

        multipoint.setVisibleWhen(() -> isBlatantMode() && isNotScreenshareMode());
    }

    private boolean isScreenshareMode() {
        return NeuroAimEngine.MODE_SCREENSHARE.equals(mode.get());
    }

    private boolean isNotScreenshareMode() {
        return !isScreenshareMode();
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

    private boolean isSmoothMode() {
        return !isBlatantMode() && !isScreenshareMode();
    }

    private boolean isBlatantMode() {
        return NeuroAimEngine.MODE_BLATANT.equals(mode.get());
    }



    public boolean getAimAssistState() {
        return this.isEnabled();
    }

    @Override
    public void onEnable() {
        engine.onEnabledChanged(true);
    }

    @Override
    public void onDisable() {
        engine.onEnabledChanged(false);
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        engine.tick(mc, this);
        applyAimRotation(mc, 1.0F);
    }

    public void onFrame(MinecraftClient mc) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        applyAimRotation(mc, mc.getRenderTickCounter().getTickDelta(true));
    }

    
    public void onPreMotion(MinecraftClient mc) {
    }

    private void applyAimRotation(MinecraftClient mc, float partial) {
        if (!isEnabled() || mc.player == null || mc.world == null) {
            return;
        }
        if (System.currentTimeMillis() < interactionPauseUntil) {
            return;
        }
        
        
        
        engine.applyFrame(mc, this, partial);
    }

    public void pauseForInteraction() {
        interactionPauseUntil = System.currentTimeMillis() + 100;
    }

    public int getAimLockedTargetId() {
        return engine.getLockedTargetId();
    }

    public boolean hasSilentRotation() {
        return false;
    }

    public float getSilentYaw() {
        return MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getYaw()
                : 0f;
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public String getMode() {
        String selected = mode.get();
        if (NeuroAimEngine.MODE_BLATANT.equals(selected)) {
            return NeuroAimEngine.MODE_BLATANT;
        }
        return NeuroAimEngine.MODE_SMOOTH;
    }

    @Override
    public String getMovementCoupling() {
        return NeuroAimEngine.COUPLING_ALWAYS;
    }

    @Override
    public float getIdleDelaySeconds() {
        return 0.0F;
    }

    @Override
    public float getSpeed() {
        return (float) speed.get();
    }

    @Override
    public float getAcceleration() {
        return (float) acceleration.get();
    }

    @Override
    public float getRange() {
        return (float) range.get();
    }

    @Override
    public float getFov() {
        return (float) fov.get();
    }

    @Override
    public float getAimHeight() {
        return (float) aimHeight.get();
    }

    @Override
    public boolean isMultipoint() {
        return isBlatantMode() && multipoint.get();
    }

    @Override
    public boolean isElytraTracking() {
        return elytraTracking.get();
    }

    @Override
    public boolean isSlotLock() {
        return slotLock.get();
    }

    @Override
    public int getLockedSlot() {
        return (int) slotField.get() - 1;
    }

    @Override
    public boolean isRequireAttackHeld() {
        return false;
    }

    @Override
    public boolean isOnlyWhileClicking() {
        return false;
    }

    public boolean isStickyAim() {
        return false;
    }

    @Override
    public boolean isAimThroughWalls() {
        return !wallsCheck.get();
    }

    @Override
    public boolean isFocusOnEnemy() {
        return focusOnEnemy.get();
    }

    @Override
    public boolean isStopAtTarget() {
        return false;
    }

    @Override
    public String getTargetPriority() {
        return "Yaw";
    }

    @Override
    public ListEntryImpl getAntibot() {
        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        return mgr != null ? (ListEntryImpl) mgr.getModuleByClass(ListEntryImpl.class) : null;
    }

    @Override
    public boolean isTeammates() {
        return teammates.get();
    }

    @Override
    public boolean isPriority() {
        return priority.get();
    }

    @Override
    public boolean isPlayersOnly() {
        return playersOnly.get();
    }
}
