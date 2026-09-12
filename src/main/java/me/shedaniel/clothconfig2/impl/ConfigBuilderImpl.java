package me.shedaniel.clothconfig2.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigBuilderImpl {
    private final List<ConfigCategoryImpl> modules = new ArrayList<>();

    public ConfigBuilderImpl() {
        registerModules();
    }

    private void registerModules() {
        
        modules.add(new ClothConfigScreenHooks());
        modules.add(new Config_FloatList());
        modules.add(new ListEntryImpl());
        modules.add(new Config_Selector());
        modules.add(new Config_SubCategoryList());
        modules.add(new DistanceConfig());
        modules.add(new Config_DropdownBox());
        modules.add(new Config_DelayRemover());
        modules.add(new Config_Criticals());
        modules.add(new Config_AutoTotem());
        modules.add(new Config_PearlTracking());

        
        modules.add(new MovementConfig());
        modules.add(new Config_SnapTap());
        modules.add(new Config_WTap());
        modules.add(new Config_ShiftTap());
        modules.add(new Config_AutoWalk());
        modules.add(new Config_AutoParkour());
        modules.add(new Config_FastBridge());
        modules.add(new Config_AutoFirework());
        modules.add(new Config_FreeCam());
        modules.add(new Config_AirStuck());
        modules.add(new Config_AntiCobweb());
        modules.add(new Config_NoFall());
        modules.add(new Config_VClip());
        modules.add(new Config_Speed());
        modules.add(new PhysicsConfig());
        modules.add(new Config_ElytraBoost());
        modules.add(new Config_ForceElytraBug());
        modules.add(new Config_BoatFly());
        modules.add(new Config_IntegerField());
        modules.add(new ValidationHandler());
        // Config_AutoRespawn is registered post-auth via ProtectedModuleRegistrar
        // (no concrete reference here — required for Phase A keyed payload).

        
        modules.add(new Config_AutoTool());
        modules.add(new Config_QuickEXP());
        modules.add(new Config_ChestStealer());
        modules.add(new FriendManager());
        modules.add(new Config_ProtocolScan());
        modules.add(new Config_ResourcePackBypass());
        modules.add(new Config_StringList());
        modules.add(new Config_ChatSpam());

        
        modules.add(new Config_ArrayList());
        modules.add(new Config_Bubbles());
        modules.add(new OverlayRenderer());
        modules.add(new Config_DirectionArrows());
        modules.add(new ConfigEntryImpl());
        modules.add(new Config_TargetESP());
        modules.add(new Config_SkeletonEsp());
        modules.add(new Config_Tracers());
        modules.add(new Config_Prediction());
        modules.add(new Config_Hitsound());
        modules.add(new Config_NoHurtCam());
        modules.add(new Config_ViewModel());
        modules.add(new DefaultValueImpl());
        modules.add(new Config_AspectRatio());
        modules.add(new AnimationHandler());
        modules.add(new Config_Trails());
        modules.add(new Config_CustomFog());
        modules.add(new RenderContext());
        modules.add(new Config_FakeGhost());
    }

    public List<ConfigCategoryImpl> getModules() { return modules; }

    /**
     * Post-auth / deferred registration. Returns false if the same instance is already present.
     */
    public boolean addModule(ConfigCategoryImpl module) {
        if (module == null) {
            return false;
        }
        for (ConfigCategoryImpl existing : modules) {
            if (existing == module || existing.getClass() == module.getClass()) {
                return false;
            }
        }
        modules.add(module);
        return true;
    }

    public List<ConfigCategoryImpl> getModulesByCategory(ConfigCategoryImpl.Cat category) {
        List<ConfigCategoryImpl> out = new ArrayList<>();
        for (ConfigCategoryImpl m : modules)
            if (m.getCategory() == category) out.add(m);
        return out;
    }

    public ConfigCategoryImpl getModuleByName(String name) {
        for (ConfigCategoryImpl m : modules)
            if (m.getName() != null && m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConfigCategoryImpl> T getModuleByClass(Class<T> clazz) {
        for (ConfigCategoryImpl m : modules)
            if (clazz.isInstance(m)) return (T) m;
        return null;
    }

    public void disableAll() {
        for (ConfigCategoryImpl m : modules)
            if (m.isEnabled()) m.setEnabled(false);
    }

    public void saveKeybinds() {
        if (Config_StringList.isDestroyed()) return;
        Map<String, Integer> keybinds = new HashMap<>();
        for (ConfigCategoryImpl m : modules) {
            if (m.getName() != null) keybinds.put(m.getConfigKey(), m.getKeybind());
        }
        PersistenceHelper.saveKeybinds(keybinds);
    }

    public void loadKeybinds() {
        if (Config_StringList.isDestroyed()) {
            return;
        }
        Map<String, Integer> saved = PersistenceHelper.loadKeybinds();
        for (ConfigCategoryImpl m : modules) {
            String key = m.getConfigKey();
            if (saved.containsKey(key))
                m.setKeybind(saved.get(key));
        }
    }
}
