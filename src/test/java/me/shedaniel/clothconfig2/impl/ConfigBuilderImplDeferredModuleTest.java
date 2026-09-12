package me.shedaniel.clothconfig2.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBuilderImplDeferredModuleTest {

    @Test
    void registerModulesDoesNotConstructAutoRespawn() {
        ConfigBuilderImpl manager = new ConfigBuilderImpl();
        for (ConfigCategoryImpl mod : manager.getModules()) {
            assertFalse(
                    mod.getClass().getName().endsWith("Config_AutoRespawn"),
                    "AutoRespawn must not be constructed by ConfigBuilderImpl");
        }
    }

    @Test
    void addModuleAcceptsDeferredInstanceOnce() {
        ConfigBuilderImpl manager = new ConfigBuilderImpl();
        ConfigCategoryImpl stub = new ConfigCategoryImpl("Deferred Stub", "test", ConfigCategoryImpl.Cat.MOVEMENT) {};
        assertTrue(manager.addModule(stub));
        assertFalse(manager.addModule(stub));
        assertTrue(manager.getModules().contains(stub));
    }
}
