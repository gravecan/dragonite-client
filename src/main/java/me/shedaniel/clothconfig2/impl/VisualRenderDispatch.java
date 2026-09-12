package me.shedaniel.clothconfig2.impl;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public final class VisualRenderDispatch {

    private static final List<Consumer<WorldRenderContext>> AFTER_ENTITIES = new ArrayList<>();
    private static final List<Consumer<WorldRenderContext>> TRANSLUCENT = new ArrayList<>();
    private static final List<HudRenderer> HUD = new ArrayList<>();
    private static final List<Consumer<MinecraftClient>> FRAME = new ArrayList<>();

    private static volatile boolean dirty = true;

    @FunctionalInterface
    private interface HudRenderer {
        void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter);
    }

    private VisualRenderDispatch() {}

    public static void purge() {
        AFTER_ENTITIES.clear();
        TRANSLUCENT.clear();
        HUD.clear();
        FRAME.clear();
        dirty = true;
    }

    public static void markDirty() {
        dirty = true;
        Config_ArrayList.invalidateModuleListCache();
    }

    public static void ensureBuilt(ConfigBuilderImpl manager) {
        if (manager == null) {
            return;
        }
        if (dirty) {
            rebuild(manager);
        }
    }

    public static void rebuild(ConfigBuilderImpl manager) {
        AFTER_ENTITIES.clear();
        TRANSLUCENT.clear();
        HUD.clear();
        FRAME.clear();

        for (ConfigCategoryImpl mod : manager.getModules()) {
            if (!mod.isEnabled()) {
                continue;
            }
            if (mod instanceof Config_Tracers tr) {
                TRANSLUCENT.add(tr::render);
            } else if (mod instanceof AnimationHandler jc) {
                TRANSLUCENT.add(jc::render);
            } else if (mod instanceof RenderContext hats) {
                TRANSLUCENT.add(hats::render);
            } else if (mod instanceof ConfigEntryImpl esp) {
                TRANSLUCENT.add(esp::render);
                HUD.add((ctx, tick) -> esp.renderHud(ctx, tick.getTickDelta(true)));
            } else if (mod instanceof Config_Selector sel) {
                TRANSLUCENT.add(sel::render);
            } else if (mod instanceof Config_SubCategoryList sh) {
                TRANSLUCENT.add(sh::render);
            } else if (mod instanceof Config_TargetESP tes) {
                TRANSLUCENT.add(tes::render);
            } else if (mod instanceof Config_Trails trails) {
                TRANSLUCENT.add(trails::render);
            } else if (mod instanceof Config_SkeletonEsp sk) {
                TRANSLUCENT.add(sk::render);
            } else if (mod instanceof Config_Prediction pred) {
                TRANSLUCENT.add(pred::render);
            } else if (mod instanceof Config_DirectionArrows da) {
                HUD.add((ctx, tick) -> da.render(ctx, tick.getTickDelta(true)));
            } else if (mod instanceof OverlayRenderer th) {
                HUD.add((ctx, tick) -> th.render(ctx));
                FRAME.add(th::onFrame);
            } else if (mod instanceof Config_Bubbles bubbles) {
                HUD.add((ctx, tick) -> bubbles.renderHud(ctx, tick.getTickDelta(true)));
            } else if (mod instanceof Config_ArrayList list) {
                HUD.add((ctx, tick) -> list.renderHud(ctx, tick.getTickDelta(true)));
            }

            if (mod instanceof Config_FloatList aa) {
                FRAME.add(aa::onFrame);
            } else if (mod instanceof ClothConfigScreenHooks hooks) {
                FRAME.add(hooks::onFrame);
            }
        }

        dirty = false;
    }

    public static void renderAfterEntities(WorldRenderContext ctx) {
        for (Consumer<WorldRenderContext> r : AFTER_ENTITIES) {
            try {
                r.accept(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    public static void renderTranslucent(WorldRenderContext ctx) {
        for (Consumer<WorldRenderContext> r : TRANSLUCENT) {
            try {
                r.accept(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    public static void onFrame(MinecraftClient mc) {
        WorldRenderEntityCache.rebuild(mc, HudConfigInit.getManager());
        for (Consumer<MinecraftClient> f : FRAME) {
            try {
                f.accept(mc);
            } catch (Exception ignored) {
            }
        }
    }

    public static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        for (HudRenderer r : HUD) {
            try {
                r.render(ctx, tickCounter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
