package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;

import net.minecraft.client.MinecraftClient;





public class Config_AspectRatio extends ConfigCategoryImpl {



    public static Config_AspectRatio INSTANCE;



    private final EnumSelectorBuilder preset;

    private final DoubleFieldBuilder customRatio;



    public Config_AspectRatio() {

        super("AspectRatio", "Change screen aspect ratio", Cat.RENDER);

        INSTANCE = this;



        preset = new EnumSelectorBuilder(
                "Ratio",
                "Viewport aspect for 3D rendering",
                "16:9",
                "16:9",
                "16:10",
                "4:3",
                "21:9",
                "Custom",
                "Native"
        );

        customRatio = new DoubleFieldBuilder("Custom", "Manual width/height ratio", 1.78, 0.5, 2.5, 0.01);

        customRatio.setVisibleWhen(() -> "Custom".equals(preset.get()));



        addSetting(preset);

        addSetting(customRatio);

    }



    public float resolveNativeAspect(MinecraftClient mc) {

        if (mc == null || mc.getWindow() == null) {

            return 16f / 9f;

        }

        return (float) mc.getWindow().getFramebufferWidth()

                / Math.max(1, mc.getWindow().getFramebufferHeight());

    }



    

    public float resolveWorldAspect(MinecraftClient mc) {

        float nativeAspect = resolveNativeAspect(mc);

        if (!isEnabled()) {

            return nativeAspect;

        }

        String mode = preset.get();

        if (mode == null || "Native".equals(mode)) {

            return nativeAspect;

        }

        float target = switch (mode) {

            case "16:9" -> 16f / 9f;

            case "16:10" -> 16f / 10f;

            case "4:3" -> 4f / 3f;

            case "21:9" -> 21f / 9f;

            case "Custom" -> (float) customRatio.get();

            default -> nativeAspect;

        };

        

        return Math.max(0.5f, Math.min(2.5f, target));

    }



    

    @Deprecated

    public float resolveAspect(MinecraftClient mc) {

        return resolveWorldAspect(mc);

    }

}


