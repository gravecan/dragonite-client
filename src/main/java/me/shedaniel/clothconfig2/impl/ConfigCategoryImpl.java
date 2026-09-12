package me.shedaniel.clothconfig2.impl;

import me.shedaniel.clothconfig2.impl.builders.AbstractFieldBuilder;

import java.util.ArrayList;
import java.util.List;


public abstract class ConfigCategoryImpl {
    private Object name;
    private String description;
    private String tooltip;  
    private final Cat category;
    private boolean enabled;
    private int keybind = -1;
    
    private boolean binding;
    public final List<AbstractFieldBuilder> settings = new ArrayList<>();

    private String cachedName = null;

    public ConfigCategoryImpl(Object name, String description, Cat category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void setName(Object name) {
        this.name = name;
        this.cachedName = null;
    }
    public void setDescription(String description) { this.description = description; }
    public void setTooltip(String tooltip) { this.tooltip = tooltip; }
    public String getTooltip() { return tooltip; }
    public List<AbstractFieldBuilder> getSettings() { return settings; }
    public void addSetting(AbstractFieldBuilder s) { settings.add(s); }

    public String getName() {
        if (cachedName == null && name != null) {
            if (name instanceof SecString) {
                cachedName = ((SecString) name).decryptToString();
            } else {
                cachedName = name.toString();
            }
        }
        return cachedName;
    }

    public void purgeCache() {
        this.cachedName = null;
    }
    public String getDescription() {
        return humanizeDescription(description);
    }

    private static String humanizeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw
                .replace(",", "")
                .replace("—", " ")
                .replace(" - ", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
    public Cat getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public int getKeybind() { return keybind; }
    public boolean isBinding() { return binding; }
    public void setKeybind(int k) {
        if (Config_StringList.isDestroyed()) {
            this.keybind = -1;
            return;
        }
        this.keybind = k;
    }

    public void setBinding(boolean b) {
        if (Config_StringList.isDestroyed()) {
            this.binding = false;
            return;
        }
        this.binding = b;
    }

    public void setEnabled(boolean enabled) {
        if (Config_StringList.isDestroyed()) {
            this.enabled = false;
            return;
        }
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
        VisualRenderDispatch.markDirty();
    }

    
    public void silentlyDisable() {
        this.enabled = false;
    }

    public void toggle() { setEnabled(!enabled); }

    public String getConfigKey() {
        try {
            String classStr = this.getClass().getName();
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(classStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (Exception e) {
            return "fallback_" + this.getClass().getSimpleName();
        }
    }

    public boolean isGuiVisible() { return true; }
    public void onEnable() {}
    public void onDisable() {}
    public void onRender(float tickDelta) {}
    public void unregisterAllListeners() {}

    public enum Cat {
        M(0xFF1a4d6e) {
            @Override
            public String getName() {
                return SecString.OBF("Render");
            }
        },
        COMBAT(0xFFff4d4d) {
            @Override
            public String getName() {
                return SecString.OBF("Combat");
            }
        },
        MOVEMENT(0xFF4dff4d) {
            @Override
            public String getName() {
                return SecString.OBF("Movement");
            }
        },
        VISUALS(0xFFa138eb) {
            @Override
            public String getName() {
                return SecString.OBF("Visuals");
            }
        },
        PLAYER(0xFF4da6ff) {
            @Override
            public String getName() {
                return SecString.OBF("Player");
            }
        },
        MISC(0xFFe6b84d) {
            @Override
            public String getName() {
                return SecString.OBF("Misc");
            }
        },
        RENDER(0xFFffb366) {
            @Override
            public String getName() {
                return SecString.OBF("Render");
            }
        },
        OTHER(0xFFcccccc) {
            @Override
            public String getName() {
                return SecString.OBF("Other");
            }
        };

        protected static String catName = "Render";
        private final int color;
        Cat(int color) { this.color = color; }
        public String getName() { return catName; }
        public int getColor() { return color; }
        public static void clearName() { catName = null; }
    }

    public static final class SecString {
        private final byte[] encrypted;
        private final byte[] sBox;
        private final int xorKey;
        private final int bitRotation;
        private final String dummy;

        public SecString(String dummy) {
            this.dummy = dummy;
            this.encrypted = null;
            this.sBox = null;
            this.xorKey = 0;
            this.bitRotation = 0;
        }

        public SecString(byte[] encrypted, byte[] sBox, int xorKey, int bitRotation) {
            this.encrypted = encrypted;
            this.sBox = sBox;
            this.xorKey = xorKey;
            this.bitRotation = bitRotation;
            this.dummy = null;
        }

        public String decryptToString() {
            if (dummy != null) return dummy;
            return decryptToString(encrypted, sBox, xorKey, bitRotation);
        }

        @Override
        public String toString() {
            return decryptToString();
        }

        public static String OBF(String s) {
            return s;
        }
        
        public static char[] decrypt(byte[] encrypted, byte[] sBox, int xorKey, int bitRotation) {
            char[] result = new char[encrypted.length];
            for (int i = 0; i < encrypted.length; i++) {
                int b = encrypted[i] & 0xFF;
                int unSBox = -1;
                for (int j = 0; j < 256; j++) {
                    if ((sBox[j] & 0xFF) == b) {
                        unSBox = j;
                        break;
                    }
                }
                if (unSBox == -1) {
                    unSBox = b;
                }
                int unRotated = rotateLeft(unSBox, bitRotation);
                int decryptedByte = unRotated ^ xorKey;
                result[i] = (char) (decryptedByte & 0xFF);
            }
            return result;
        }

        public static String decryptToString(byte[] encrypted, byte[] sBox, int xorKey, int bitRotation) {
            char[] chars = decrypt(encrypted, sBox, xorKey, bitRotation);
            String str = new String(chars);
            wipe(chars);
            return str;
        }

        public static void wipe(char[] array) {
            if (array != null) {
                for (int i = 0; i < array.length; i++) {
                    array[i] = '\0';
                }
            }
        }

        private static int rotateLeft(int val, int count) {
            int shift = count % 8;
            if (shift == 0) {
                return val & 0xFF;
            }
            return ((val << shift) | (val >>> (8 - shift))) & 0xFF;
        }
    }
}
