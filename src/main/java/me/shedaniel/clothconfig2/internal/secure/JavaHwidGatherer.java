package me.shedaniel.clothconfig2.internal.secure;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * HWID gathering. Release builds use the same concatenated format as {@code HWID.cpp}
 * (volume serial, computer name, CPU id string, MAC, MachineGuid) — not a SHA-256 digest.
 */
public class JavaHwidGatherer {

    private static String cachedHwid = null;
    private static String cachedGuid = null;

    public static String getHwid() {
        if (cachedHwid != null) {
            return cachedHwid;
        }
        cachedHwid = generateHwidNativeStyle();
        return cachedHwid;
    }

    public static String getMachineGuid() {
        if (cachedGuid != null) {
            return cachedGuid;
        }
        cachedGuid = readMachineGuidFromRegistry();
        if (cachedGuid == null || cachedGuid.isEmpty()) {
            cachedGuid = "unknown-guid";
        }
        return cachedGuid;
    }

    /** Mirrors native {@code GenerateHWID()} output shape for auth server compatibility. */
    private static String generateHwidNativeStyle() {
        try {
            StringBuilder ss = new StringBuilder();

            String vol = queryWmicSingleValue("logicaldisk", "VolumeSerialNumber", "where \"DeviceID='C:'\"");
            if (vol != null && !vol.isEmpty()) {
                ss.append(vol.trim().toLowerCase());
            }

            String computer = System.getenv("COMPUTERNAME");
            if (computer != null && !computer.isEmpty()) {
                ss.append('-').append(computer);
            }

            String cpuId = queryWmicSingleValue("cpu", "ProcessorId", null);
            if (cpuId != null && !cpuId.isEmpty()) {
                ss.append('-').append(cpuId.trim().toLowerCase());
            }

            String mac = firstHardwareMacHex();
            if (mac != null && !mac.isEmpty()) {
                ss.append('-').append(mac);
            }

            String mguid = readMachineGuidFromRegistry();
            if (mguid != null && !mguid.isEmpty()) {
                ss.append('-').append(mguid);
            }

            if (ss.length() > 0) {
                return ss.toString();
            }
        } catch (Exception ignored) {
        }
        return "unknown-hwid-fallback";
    }

    private static String firstHardwareMacHex() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni == null || ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length < 6) {
                    continue;
                }
                boolean allZero = true;
                for (byte b : mac) {
                    if (b != 0) {
                        allZero = false;
                        break;
                    }
                }
                if (allZero) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    sb.append(String.format("%02x", b & 0xFF));
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String queryWmicSingleValue(String alias, String property, String whereClause) {
        try {
            String[] cmd = whereClause == null
                    ? new String[] {"wmic", alias, "get", property}
                    : new String[] {"wmic", alias, whereClause, "get", property};
            Process process = Runtime.getRuntime().exec(cmd);
            process.getOutputStream().close();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.equalsIgnoreCase(property)) {
                        continue;
                    }
                    return line;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String readMachineGuidFromRegistry() {
        try {
            Process process = Runtime.getRuntime().exec(new String[] {
                    "reg", "query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"
            });
            process.getOutputStream().close();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("REG_SZ")) {
                        return line.split("REG_SZ")[1].trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
