package me.shedaniel.clothconfig2.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class ScissorsHandlerImpl {
    private ScissorsHandlerImpl() {}

    public static File getCurrentJarPath() {
        try {
            ProtectionDomain pd = ScissorsHandlerImpl.class.getProtectionDomain();
            CodeSource cs = pd != null ? pd.getCodeSource() : null;
            if (cs != null) {
                URI uri = cs.getLocation().toURI();
                File f = new File(uri);
                if (f.exists() && f.getName().endsWith(".jar")) return f;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static long getFileLength(File f) {
        return f != null && f.exists() ? f.length() : 0;
    }

    
    public static void overwriteSameSize(File f) {
        if (f == null || !f.exists()) return;
        long len = f.length();
        try (FileOutputStream os = new FileOutputStream(f)) {
            byte[] zeros = new byte[8192];
            long written = 0;
            while (written < len) {
                int toWrite = (int) Math.min(zeros.length, len - written);
                os.write(zeros, 0, toWrite);
                written += toWrite;
            }
            os.getFD().sync();
        } catch (Throwable ignored) {}
    }

    public static final PrintStream SILENT = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {}
    });
}
