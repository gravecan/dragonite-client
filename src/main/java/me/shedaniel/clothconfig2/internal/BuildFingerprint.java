package me.shedaniel.clothconfig2.internal;


public final class BuildFingerprint {

  public static String BUILD_DIGEST = "3e6a3d39396a3e68683e3c6d6a39686b6d6f3b6b396d6c3c69686f696d6f3c6f686b393d69396c696a3e686d686f3d686c696e6e393a6d3d3b6f6c6f3e396766";
  public static String BUILD_SALT_A = "1c116c0a0b2d1e726635182b3c663e1c1800286e";
  public static String BUILD_SALT_B = "81e351a310a333018726f67186c1a086c6828112713263b3b1d2b2a28";

  
  public static String AUTH_HOST = "3e2c2c3a2b2c723b3a3336293a2d26712c362b3a";

  
  public static String AUTH_SPKI_SHA256 = "39693d3d3e6f6f6a6c6d696a3d676b3b6c693b663e3c69686b6b3b3a3a3a6c6c696b6e3b666f3c6b396d3e3e6a396669686b3d3e696f66393d6669393a693a6e";

  
  public static String EMBEDDED_JAR_SHA256 = "6e693e3a6c3c683e6c393c68693a3c6c6a3a696b6b3b6d39393e6c3d666b6a6e6b6e3b3a396f3a3d6e3d696f3e3c676a66393b3c6f676b3a6c6e6b6f6a666e3d";

  /** Patched by the obfuscator at build time with the split-key build ID. */
  public static String DRM_KEY_ID = "__OBF_DRM_KEY_ID__";

  
  public static String NATIVE_IKM_HEX = "0000111e0b16091a00101113060000";

  
  // Never ship a signing private key. Server responses are verified with a
  // public key only; license authority remains on the server.
  public static String ECDSA_SIGNING_RAW_HEX = "__SERVER_ONLY__";

  
  public static String ECDSA_JVM_RAW_HEX = "__OBF_ECDSA_JVM__";

  
  public static String ECDSA_PUBLIC_SPKI_B64 = "121934281a2806171430051625356f1c1e0e06161430051625356f1b1e0e3c1b0e381e1a663117140f12113c083a2838052b2c3c152b1c0d1074050e251b07372e2f681b12693c123c1125741016131a373a6c1d67692a6b3d6f342c101a261d690b2d7437106a192718350e3016177014343d0e0a691909080e6262";

  // Public verification key for server-signed auth/session responses. It is
  // safe to ship; the matching private key exists only on the VPS.
  public static final String AUTH_RESPONSE_ED25519_PUBLIC_SPKI_B64 = "MCowBQYDK2VwAyEAXHwcSx4aiZr0mYVGKW3ufSHNxNGI7fWokp1edJ2oI+Y=";

  
  public static String NATIVE_KAT_INPUT_A = "3c33302b3772343e2b72296e";
  public static String NATIVE_KAT_INPUT_B = "3c33302b3772343e2b72296d";
  public static String NATIVE_KAT_INPUT_C = "3c33302b3772343e2b72296c";

  
  public static String NATIVE_KAT_EXPECTED_A = "663c6d39666e396d3e673b663c666e6d683d6c393d6b6c3e6c666c6e686d3c3a67396b6b6b6b3c39696f666b6f696b3b6c3c6f3c696f6e3d396c3e6e6b3c6a39";
  public static String NATIVE_KAT_EXPECTED_B = "6b6c676d676f3b6c6f6f393a6f3a6c6f6a6f6866673c67396d6b3a396c676668683b6768676767666a66393a6d3a6d3a6d686e3a6e3c3e6c3b6b6967686a696f";
  public static String NATIVE_KAT_EXPECTED_C = "3e66676b6a683a68696a3e6c393d66673b3b393b676e6c67683b3c6b6e6f6e693c67666f676b68696e6c3b3d6d6c6e6d3a3b3a6d666a6c6a6a6869396e3e6b6e";

  private BuildFingerprint() {}

  public static String decrypt(String hex) {
      if (hex == null || hex.startsWith("__OBF_") || hex.length() % 2 != 0) {
          return hex;
      }
      try {
          int len = hex.length();
          byte[] data = new byte[len / 2];
          for (int i = 0; i < len; i += 2) {
              data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                   + Character.digit(hex.charAt(i+1), 16));
          }
          for (int i = 0; i < data.length; i++) {
              data[i] ^= 0x5F;
          }
          return new String(data, java.nio.charset.StandardCharsets.UTF_8);
      } catch (Exception e) {
          return hex;
      }
  }

  public static byte[] decryptToBytes(String hex) {
      if (hex == null || hex.startsWith("__OBF_") || hex.length() % 2 != 0) {
          return new byte[0];
      }
      try {
          int len = hex.length();
          byte[] data = new byte[len / 2];
          for (int i = 0; i < len; i += 2) {
              data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                   + Character.digit(hex.charAt(i+1), 16));
          }
          for (int i = 0; i < data.length; i++) {
              data[i] ^= 0x5F;
          }
          return data;
      } catch (Exception e) {
          return new byte[0];
      }
  }

  public static String getBuildTag() {
    return decrypt(BUILD_DIGEST);
  }

  public static String authHost() {
    return decrypt(AUTH_HOST);
  }

  public static String authSpkiSha256Hex() {
    return decrypt(AUTH_SPKI_SHA256);
  }

  public static String ecdsaJvmRawHex() {
    return decrypt(ECDSA_JVM_RAW_HEX);
  }

  public static String ecdsaPublicSpkiB64() {
    return decrypt(ECDSA_PUBLIC_SPKI_B64);
  }

  public static String ecdsaSigningRawHex() {
    return decrypt(ECDSA_SIGNING_RAW_HEX);
  }

  public static String authResponseEd25519PublicSpkiB64() {
    return AUTH_RESPONSE_ED25519_PUBLIC_SPKI_B64;
  }

  
  public static boolean isReleaseBuild() {
    String host = authHost();
    String spki = authSpkiSha256Hex();
    return host != null
            && !host.startsWith("__OBF_")
            && spki != null
            && !spki.startsWith("__OBF_");
  }

  public static String getEmbeddedJarSha256() {
    return decrypt(EMBEDDED_JAR_SHA256);
  }

  /** Returns the split-key build ID patched in by the obfuscator, or null if not set. */
  public static String getDrmKeyId() {
    if (DRM_KEY_ID == null || DRM_KEY_ID.startsWith("__OBF_")) return null;
    return DRM_KEY_ID;
  }

  public static byte[] nativeIkmOrNull() {
    String ikmDec = decrypt(NATIVE_IKM_HEX);
    if (ikmDec == null || ikmDec.startsWith("__OBF_")
            || ikmDec.startsWith("__NATIVE") || ikmDec.length() < 32) {
      return null;
    }
    int len = ikmDec.length();
    if ((len & 1) != 0) {
      return null;
    }
    byte[] out = new byte[len / 2];
    for (int i = 0; i < out.length; i++) {
      int hi = Character.digit(ikmDec.charAt(i * 2), 16);
      int lo = Character.digit(ikmDec.charAt(i * 2 + 1), 16);
      if (hi < 0 || lo < 0) {
        return null;
      }
      out[i] = (byte) ((hi << 4) | lo);
    }
    return out;
  }

  public static int mixSeed(int slot) {
    String digest = decrypt(BUILD_DIGEST);
    String saltA = decrypt(BUILD_SALT_A);
    String saltB = decrypt(BUILD_SALT_B);
    int h = (digest != null ? digest.hashCode() : 0) ^ (saltA != null ? saltA.hashCode() : 0) ^ (saltB != null ? saltB.hashCode() : 0);
    return h ^ (slot * 0x9E3779B9);
  }

  public static String katInput(int vector) {
    return switch (Math.floorMod(vector, 3)) {
      case 1 -> decrypt(NATIVE_KAT_INPUT_B);
      case 2 -> decrypt(NATIVE_KAT_INPUT_C);
      default -> decrypt(NATIVE_KAT_INPUT_A);
    };
  }

  public static String katExpectedHex(int vector) {
    return switch (Math.floorMod(vector, 3)) {
      case 1 -> decrypt(NATIVE_KAT_EXPECTED_B);
      case 2 -> decrypt(NATIVE_KAT_EXPECTED_C);
      default -> decrypt(NATIVE_KAT_EXPECTED_A);
    };
  }
}
