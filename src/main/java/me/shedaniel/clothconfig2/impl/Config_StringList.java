package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.internal.AuthGate;
import net.minecraft.client.MinecraftClient;

public final class Config_StringList extends ConfigCategoryImpl {
    private static boolean destroyed = false;
    private boolean confirmationPending = false;

    public static boolean isDestroyed() {
        return destroyed;
    }

    public Config_StringList() {
        super("SelfDestruct", "Removes all traces of client", Cat.MISC);
        setTooltip("Instantly removes client from memory and clears all traces");
    }

    public boolean isConfirmationPending() {
        return confirmationPending;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (destroyed) {
            return;
        }
        if (enabled) {
            super.setEnabled(true);
        } else {
            if (confirmationPending) {
                runDestruct();
            } else {
                super.setEnabled(false);
            }
        }
    }

    @Override
    public void onEnable() {
        if (destroyed) {
            return;
        }
        if (!confirmationPending) {
            confirmationPending = true;
            return;
        }
        runDestruct();
    }

    @Override
    public void onDisable() {
        if (destroyed) {
            return;
        }
        confirmationPending = false;
    }

    private void runDestruct() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        confirmationPending = false;

        try {
            System.setOut(ScissorsHandlerImpl.SILENT);
            System.setErr(ScissorsHandlerImpl.SILENT);
        } catch (Throwable ignored) {
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen != null) {
            mc.setScreen(null);
        }

        HudConfigInit.onSelfDestruct();
        GameOptionsHooks.shutdown();
        PersistenceHelper.clearKeybinds();
        AuthGate.invalidateCache();

        ConfigBuilderImpl mgr = HudConfigInit.getManager();
        if (mgr != null) {
            for (ConfigCategoryImpl m : mgr.getModules()) {
                try {
                    m.silentlyDisable();
                    m.setKeybind(-1);
                    m.setBinding(false);
                } catch (Throwable ignored) {
                }
            }
        }

        // Run memory purging in a delayed background thread to avoid concurrent rendering NullPointerExceptions
        new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            
            ConfigCategoryImpl.Cat.clearName();
            if (mgr != null) {
                for (ConfigCategoryImpl m : mgr.getModules()) {
                    try {
                        if (m != this) {
                            m.unregisterAllListeners();
                            m.setName(null);
                            m.setDescription(null);
                            for (var s : m.getSettings()) {
                                s.clearStrings();
                            }
                            m.getSettings().clear();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            VisualRenderDispatch.purge();
            silentlyDisable();
        }, "selfdestruct-purge").start();
    }
}
