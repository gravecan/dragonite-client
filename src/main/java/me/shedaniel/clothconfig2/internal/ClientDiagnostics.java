package me.shedaniel.clothconfig2.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;

public final class ClientDiagnostics {

  public static final String BUILD_TAG = "2026-06-21-verifyfix12";

  private static volatile boolean installed;

  private ClientDiagnostics() {}

  public static void install() {
    if (installed) {
      return;
    }
    installed = true;

    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      log("UNCAUGHT on " + thread.getName(), throwable);
      throwable.printStackTrace();
    });

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      writeLine("JVM shutdown hook fired at " + Instant.now());
    }, "cloth-shutdown"));
  }

  public static void fatalHalt(String reason) {
    writeLine("FATAL HALT: " + reason + " at " + Instant.now());
    System.err.println("[ClothConfig] FATAL: " + reason);
    try {
      Thread.sleep(250L);
    } catch (InterruptedException ignored) {
    }
    Runtime.getRuntime().halt(1);
  }

  public static void log(String message, Throwable throwable) {
    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    writeLine(message + "\n" + sw);
  }

  private static void writeLine(String line) {
    // No cloth-last-exit.log — keep fatal detail on stderr only for this process.
    System.err.println("[ClothConfig] " + line);
  }

  private static Path logPath() {
    return Path.of(".");
  }
}
