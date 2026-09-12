package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.internal.AuthGate;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public class HudConfigInit {
    private static ConfigBuilderImpl manager;
    private static volatile boolean guiKeyDisabled = false;
    private static boolean eventsRegistered = false;

    public static void init() {
        System.out.println("[ClothConfig] HudConfigInit.init() START");
        
        try {
            GameOptionsHooks.init();
            System.out.println("[ClothConfig] GameOptionsHooks.init() DONE, active=" + GameOptionsHooks.isActive());
        } catch (Throwable t) {
            System.out.println("[ClothConfig] GameOptionsHooks.init() failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        
        if (!eventsRegistered) {
            eventsRegistered = true;
            
            AttackEntityCallback.EVENT.register((PlayerEntity player, World world, Hand hand, net.minecraft.entity.Entity entity, EntityHitResult hitResult) -> {
                if (Config_StringList.isDestroyed()) return ActionResult.PASS;
                if (manager == null || player != MinecraftClient.getInstance().player) return ActionResult.PASS;
                if (entity instanceof LivingEntity target && target.isAlive() && target != player) {
                    if (Config_ShiftTap.INSTANCE != null && Config_ShiftTap.INSTANCE.isEnabled()) {
                        Config_ShiftTap.INSTANCE.onAttack();
                    }
                    if (Config_WTap.INSTANCE != null && Config_WTap.INSTANCE.isEnabled()) {
                        Config_WTap.INSTANCE.onAttack();
                    }
                    for (ConfigCategoryImpl mod : manager.getModules()) {
                        if (mod instanceof OverlayRenderer th && th.isEnabled()) {
                            th.setCallbackTarget(target, System.currentTimeMillis());
                        }
                    }
                }
                return ActionResult.PASS;
            });

            WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
                if (Config_StringList.isDestroyed()) return;
                if (!AuthGate.allowFeatures()) return;
                if (manager == null) return;
                VisualRenderDispatch.ensureBuilt(manager);
                VisualRenderDispatch.renderAfterEntities(ctx);
            });

            WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
                if (Config_StringList.isDestroyed()) return;
                if (!AuthGate.allowFeatures()) return;
                if (manager == null) return;
                VisualRenderDispatch.ensureBuilt(manager);
                VisualRenderDispatch.renderTranslucent(ctx);
            });

            HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
                if (Config_StringList.isDestroyed()) return;
                if (!AuthGate.allowFeatures()) return;
                if (manager == null) return;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null && mc.world != null && VisualPreview.allowHudVisuals(mc)) {
                    VisualRenderDispatch.ensureBuilt(manager);
                    VisualRenderDispatch.onFrame(mc);
                    VisualRenderDispatch.renderHud(drawContext, tickCounter);
                }
            });
        }
    }

    public void onInitializeClient() {
        init();
    }

    public static ConfigBuilderImpl getManager() {
        if (Config_StringList.isDestroyed()) {
            return manager;
        }
        if (manager == null) {
            manager = new ConfigBuilderImpl();
            VisualRenderDispatch.rebuild(manager);
        }
        return manager;
    }

    public static void onSelfDestruct() {
        guiKeyDisabled = true;
        if (manager != null) {
            for (ConfigCategoryImpl mod : manager.getModules()) {
                mod.silentlyDisable();
                mod.setKeybind(-1);
                mod.setBinding(false);
            }
        }
        VisualRenderDispatch.purge();
    }

    public static void setGuiKeyDisabled(boolean disabled) {
        guiKeyDisabled = disabled;
    }

    public static void openConfigGui() {
        if (Config_StringList.isDestroyed()) {
            return;
        }
        if (guiKeyDisabled) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Authenticated check: if not authenticated, do not allow opening the config GUI.
        if (!me.shedaniel.clothconfig2.internal.SessionHandler.getInstance().isAuthenticated()) {
            return;
        }

        if (!AuthGate.allowGui()) {
            return;
        }

        Runnable open = HudConfigInit::openConfigGuiOnClientThread;
        if (mc.isOnThread()) {
            open.run();
        } else {
            mc.execute(open);
        }
    }

    private static void openConfigGuiOnClientThread() {
        try {
            System.out.println("[ClothConfig] Calling ClothConfigScreen.open()...");
            me.shedaniel.clothconfig2.gui.ClothConfigScreen.open();
            System.out.println("[ClothConfig] ClothConfigScreen.open() completed");
        } catch (Throwable t) {
            System.out.println("[ClothConfig] ERROR opening GUI: " + t.getMessage());
            t.printStackTrace();
            if (!Config_StringList.isDestroyed()) {
                manager = new ConfigBuilderImpl();
                try {
                    me.shedaniel.clothconfig2.gui.ClothConfigScreen.open();
                } catch (Throwable retry) {
                    System.out.println("[ClothConfig] GUI retry failed: " + retry.getMessage());
                }
            }
        }
    }

    /**
     * Runs at {@code sendMovementPackets} HEAD — before any flying packet.
     * Silent slot changes, attacks, and item uses MUST happen here or Grim/Vulcan
     * Post (held item change) will flag them as post-flying.
     */
    /**
     * Runs at {@code sendMovementPackets} HEAD — before any flying packet.
     * Silent slot changes, attacks, and item uses MUST happen here or Grim/Vulcan
     * Post (held item change) will flag them as post-flying.
     */
    public static void onBeforeMovementPackets(MinecraftClient mc) {
        if (Config_StringList.isDestroyed()) {
            return;
        }
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!AuthGate.mixinGate() || !AuthGate.allowFeatures()) {
            return;
        }

        DeferredSlotRestore.tick(mc);

        Config_DropdownBox shieldBreaker = Config_DropdownBox.getInstance();
        if (shieldBreaker != null && shieldBreaker.isEnabled()) {
            shieldBreaker.tick(mc);
        }

        Config_AutoFirework autoFw = Config_AutoFirework.INSTANCE;
        if (autoFw != null && autoFw.isEnabled()) {
            autoFw.onMovementTick(mc);
        }

        Config_PearlTracking pearl = Config_PearlTracking.INSTANCE;
        if (pearl != null && pearl.isEnabled()) {
            pearl.tick(mc);
        }

        ClothConfigScreenHooks triggerbot = ClothConfigScreenHooks.INSTANCE;
        if (triggerbot != null && triggerbot.isEnabled()) {
            triggerbot.onTick(mc);
        }
    }

    public static void onGameTick() {
        onPreGameTick();
    }

    /**
     * Player tick work that normally runs from {@code ClientPlayerEntityMixin} at tick HEAD.
     * Also invoked from {@link GameOptionsHooks} so injected clients (no mixins) still run modules.
     */
    public static void onClientTickEnd(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }
        if (!AuthGate.mixinGate()) {
            return;
        }
        ClientPlayerEntity player = mc.player;

        Config_BoatFly boatFly = Config_BoatFly.INSTANCE;
        if (boatFly != null && boatFly.isBlockingShiftDismount()) {
            if (player.getVehicle() instanceof BoatEntity) {
                player.setSneaking(false);
                if (player.input != null) {
                    player.input.sneaking = false;
                }
            }
        }

        // AutoFirework / ShieldBreaker / TriggerBot / PearlTracking run in
        // onBeforeMovementPackets (pre-flying). Do not run them here.

        Config_FreeCam fc = Config_FreeCam.INSTANCE;
        if (fc != null && fc.isEnabled()) {
            fc.tick(mc);
        }

        onPreGameTick();
    }

    public static void onPreGameTick() {
        if (Config_StringList.isDestroyed()) {
            return;
        }
        if (manager == null) manager = new ConfigBuilderImpl();
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        AuthGate.enforceSession();
        if (!AuthGate.allowFeatures()) {
            return;
        }
        AuthGate.runDistributedChecks();
        Config_Speed speed = manager.getModuleByClass(Config_Speed.class);
        if (speed != null) {
            speed.tick(mc);
        }
        OverlayRenderer targetHud = manager.getModuleByClass(OverlayRenderer.class);
        if (targetHud != null && targetHud.isEnabled()) {
            HitRegistration.tick(mc);
        }
        MouseSimulation.tick();
        CombatMovementSync.tick();

        // Auto-reenable Teleport Check disabled modules
        me.shedaniel.clothconfig2.impl.ClothConfigScreenHooks triggerbot = me.shedaniel.clothconfig2.impl.ClothConfigScreenHooks.INSTANCE;
        if (triggerbot != null) triggerbot.checkTempReenable();

        me.shedaniel.clothconfig2.impl.Config_FloatList aimassist1 = me.shedaniel.clothconfig2.impl.Config_FloatList.INSTANCE;
        if (aimassist1 != null) aimassist1.checkTempReenable();

        try {
            for (ConfigCategoryImpl mod : manager.getModules()) {
                if (!mod.isEnabled()) continue;
                if (mod instanceof ListEntryImpl ab) {
                    ab.tick();
                    continue;
                }
                
                try {
                    tickEnabledModule(mod, mc);
                } catch (Throwable t) {
                    System.out.println("[ClothConfig] module tick failed ("
                            + mod.getName() + "): " + t.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            System.out.println("[ClothConfig] onPreGameTick error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void tickEnabledModule(ConfigCategoryImpl mod, MinecraftClient mc) {
        // Deferred/secure-payload modules: no concrete Config_* names here.
        if (mod instanceof ClientTickHook hook) {
            hook.onClientTick(mc);
            return;
        }
        if (mod instanceof Config_DelayRemover dr) {
            dr.tick(mc);
            return;
        }
        if (mod instanceof Config_FloatList aa) {
            aa.tick(mc);
            return;
        }
        if (mod instanceof Config_Selector hb) {
            hb.tick();
        } else if (mod instanceof Config_IntegerField es) {
            es.tick(mc);
        } else if (mod instanceof ClothConfigScreenHooks bt) {
            // Combat packet work runs in onBeforeMovementPackets (pre-flying).
        } else if (mod instanceof Config_PearlTracking pearl) {
            // Pearl throws run in onBeforeMovementPackets (pre-flying).
        } else if (mod instanceof Config_ChatSpam spam) {
            spam.tick(mc);
        } else if (mod instanceof AnimationHandler jc) {
            jc.onTick();
        } else if (mod instanceof ValidationHandler fb) {
            fb.tick(mc);
        } else if (mod instanceof DistanceConfig rb) {
            rb.tick(mc);
        } else if (mod instanceof FriendManager fm) {
            fm.tick();
        } else if (mod instanceof MovementConfig sb) {
            sb.tick(mc);
        } else if (mod instanceof Config_NoHurtCam nhc) {
            nhc.tick(mc);
        } else if (mod instanceof Config_AirStuck air) {
            air.tick(mc);
        } else if (mod instanceof Config_BoatFly bf) {
            bf.tick(mc);
        } else if (mod instanceof Config_NoFall nf) {
            nf.tick(mc);
        } else if (mod instanceof Config_Trails tr) {
            tr.tick(mc);
        } else if (mod instanceof Config_ShiftTap st) {
            st.tick(mc);
        } else if (mod instanceof Config_WTap wt) {
            wt.tick(mc);
        } else if (mod instanceof Config_AutoTool atool) {
            atool.tick(mc);
        } else if (mod instanceof Config_ForceElytraBug elytra) {
            elytra.tick(mc);
        } else if (mod instanceof Config_AutoTotem at) {
            at.tick(mc);
        } else if (mod instanceof Config_ProtocolScan ac) {
            ac.tick(mc);
        } else if (mod instanceof Config_DropdownBox box) {
            // ShieldBreaker runs in onBeforeMovementPackets (pre-flying).
        } else if (mod instanceof Config_QuickEXP qexp) {
            qexp.tick(mc);
        } else if (mod instanceof Config_ChestStealer steal) {
            steal.tick(mc);
        } else if (mod instanceof Config_FastBridge bridge) {
            bridge.tick(mc);
        }
    }

    public static void onPostGameTick() {
        if (Config_StringList.isDestroyed()) {
            return;
        }
        if (manager == null) manager = new ConfigBuilderImpl();
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        AuthGate.enforceSession();
        if (!AuthGate.allowFeatures()) return;

        // DeferredSlotRestore must NOT run here — post-flying HeldItemChange
        // is exactly Grim/Vulcan Post (held item change).

        for (ConfigCategoryImpl mod : manager.getModules()) {
            if (!mod.isEnabled()) continue;
            if (mod instanceof ClothConfigScreenHooks h) h.onPostMotion(mc);
        }
    }

    private static String _d(int[] data, int key) {
        char[] chars = new char[data.length];
        for (int i = (1 - 1); i < data.length; i++) {
            chars[i] = (char) (data[i] ^ key);
        }
        return new String(chars);
    }

    private static boolean isOurLog(String s) {
        int k = (45 * 2);
        return s.contains(_d(new int[]{1, 25, 54, 53, 46, 50, 25, 53, 52, 60, 51, 61, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 63, 57, 47, 40, 51, 46, 35, 24, 40, 51, 62, 61, 63, 7}, k)) 
            || s.contains(_d(new int[]{1, 19, 52, 46, 63, 61, 40, 59, 46, 51, 53, 52, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 30, 51, 59, 54, 53, 61, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 20, 63, 46, 45, 53, 40, 49, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 63, 41, 41, 51, 53, 52, 18, 59, 52, 62, 54, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 25, 53, 52, 60, 51, 61, 22, 53, 59, 62, 63, 40, 7}, k)) 
            || s.contains(_d(new int[]{1, 25, 54, 51, 63, 52, 46, 30, 51, 59, 61, 52, 53, 41, 46, 51, 57, 41, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 14, 30, 21, 15, 14, 7}, k)) 
            || s.contains(_d(new int[]{1, 9, 14, 30, 31, 8, 8, 7}, k)) 
            || s.contains(_d(new int[]{1, 20, 59, 46, 51, 44, 63, 7}, k)) 
            || s.contains(_d(new int[]{1, 31, 62, 104, 111, 111, 107, 99, 9, 51, 61, 52, 7}, k)) 
            || s.contains(_d(new int[]{54, 53, 61, 53, 5, 59, 57, 57, 63, 41, 41, 53, 40, 5, 51, 52, 46, 63, 40, 52, 59, 54}, k)) 
            || s.contains(_d(new int[]{9, 46, 59, 46, 63, 9, 63, 40, 51, 59, 54, 51, 32, 63, 40}, k)) 
            || s.contains(_d(new int[]{10, 51, 42, 63, 54, 51, 52, 63, 23, 59, 52, 51, 60, 63, 41, 46}, k));
    }
}
