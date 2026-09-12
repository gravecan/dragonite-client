package me.shedaniel.clothconfig2.impl;



import me.shedaniel.clothconfig2.impl.builders.DoubleFieldBuilder;

import net.minecraft.client.MinecraftClient;

import net.minecraft.item.Items;

import org.lwjgl.glfw.GLFW;





public class Config_QuickEXP extends ConfigCategoryImpl {



    public static Config_QuickEXP INSTANCE;



    private final DoubleFieldBuilder delay;

    private long nextClickAt;



    public Config_QuickEXP() {

        super("QuickEXP", "Spams experience bottles while holding right-click", Cat.MISC);

        INSTANCE = this;

        delay = new DoubleFieldBuilder("Delay", "Ms between throws", 40, 20, 500, 1);

        addSetting(delay);

    }



    public void tick(MinecraftClient mc) {

        if (!isEnabled() || mc.player == null || mc.currentScreen != null) {

            return;

        }

        if (!mc.isWindowFocused()) {

            return;

        }

        long handle = mc.getWindow().getHandle();

        if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {

            return;

        }

        if (!mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {

            return;

        }

        long now = System.currentTimeMillis();

        if (now < nextClickAt || MouseSimulation.isSimulatingClick) {

            return;

        }

        MouseSimulation.mouseClickAsync(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 35);

        nextClickAt = now + (long) delay.get();

    }

}


