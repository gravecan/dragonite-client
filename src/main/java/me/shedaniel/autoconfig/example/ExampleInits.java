package me.shedaniel.autoconfig.example;

import me.shedaniel.clothconfig2.impl.HudConfigInit;


public final class ExampleInits {
    private ExampleInits() {}

    public static void exampleCommonInit() {
        System.out.println("[ClothConfig] ExampleInits.exampleCommonInit() CALLED - entrypoint working!");
        try {
            new HudConfigInit().onInitializeClient();
            System.out.println("[ClothConfig] HudConfigInit.onInitializeClient() completed successfully");
        } catch (Throwable t) {
            System.out.println("[ClothConfig] ERROR in entrypoint: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
