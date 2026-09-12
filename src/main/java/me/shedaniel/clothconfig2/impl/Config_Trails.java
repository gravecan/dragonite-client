package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.ColorFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


public class Config_Trails extends ConfigCategoryImpl {
    public static Config_Trails INSTANCE;

    private static final int MAX_POINTS = 320;
    private static final double SAMPLE_STEP = 0.028;

    private final ColorFieldBuilder color;
    private final DoubleFieldBuilder lifetime;
    private final DoubleFieldBuilder height;
    private final DoubleFieldBuilder fill;
    private final DoubleFieldBuilder minSpeed;

    private final Deque<TrailRibbonRenderer.TrailSample> points = new ArrayDeque<>();
    private Vec3d lastSample;

    public Config_Trails() {
        super("Trails", "Smooth trail behind you", Cat.VISUALS);
        INSTANCE = this;
        color = new ColorFieldBuilder("Color", "Trail color", 120, 179, 255).withRainbowOption();
        lifetime = new DoubleFieldBuilder("Lifetime", "Seconds before trail fades", 0.75, 0.25, 2.0, 0.25);
        lifetime.setSuffix("s");
        height = new DoubleFieldBuilder("Height", "Wall height (player height multiplier)", 0.5, 0.35, 1.2, 0.05);
        fill = new DoubleFieldBuilder("Fill", "Inner fill strength", 0.85, 0.2, 1.0, 0.05);
        minSpeed = new DoubleFieldBuilder("Min Speed", "Minimum horizontal speed to draw", 0.04, 0.0, 0.3, 0.01);
        addSetting(color);
        addSetting(lifetime);
        addSetting(height);
        addSetting(fill);
        addSetting(minSpeed);
    }

    @Override
    public void onDisable() {
        points.clear();
        lastSample = null;
    }

    public void tick(MinecraftClient mc) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = (long) (lifetime.get() * 1000.0);
        points.removeIf(p -> now - p.time() > maxAge);
        while (points.size() > MAX_POINTS) {
            points.removeFirst();
        }
    }

    public void render(WorldRenderContext ctx) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }

        float tickDelta = ctx.tickCounter().getTickDelta(true);
        long now = System.currentTimeMillis();
        Vec3d head = TrailRibbonRenderer.playerFeet(mc, tickDelta);
        addSamples(head, now);

        if (points.isEmpty()) {
            return;
        }

        RainbowManager rainbowMgr = RainbowManager.getInstance();
        if (color.isRainbow()) {
            rainbowMgr.update(1.0f);
        }

        List<TrailRibbonRenderer.TrailSample> renderSamples = new ArrayList<>(points);
        renderSamples.add(new TrailRibbonRenderer.TrailSample(head, now));
        if (renderSamples.size() < 2) {
            return;
        }

        TrailRibbonRenderer.render(
                ctx.matrixStack().peek().getPositionMatrix(),
                ctx.camera().getPos(),
                mc,
                renderSamples,
                (float) height.get(),
                (float) fill.get(),
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.isRainbow(),
                rainbowMgr
        );
    }

    private void addSamples(Vec3d sample, long now) {
        if (lastSample != null) {
            double dx = sample.x - lastSample.x;
            double dz = sample.z - lastSample.z;
            if (Math.sqrt(dx * dx + dz * dz) < minSpeed.get()) {
                return;
            }

            double dist = sample.distanceTo(lastSample);
            if (dist >= SAMPLE_STEP) {
                int steps = Math.max(1, (int) Math.ceil(dist / SAMPLE_STEP));
                for (int i = 1; i <= steps; i++) {
                    double t = i / (double) steps;
                    points.addLast(new TrailRibbonRenderer.TrailSample(lastSample.lerp(sample, t), now));
                }
            }
        } else {
            points.addLast(new TrailRibbonRenderer.TrailSample(sample, now));
        }
        lastSample = sample;

        while (points.size() > MAX_POINTS) {
            points.removeFirst();
        }
    }
}
