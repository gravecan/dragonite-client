#pragma once

#include <jni.h>
#include <string>
#include <windows.h>

namespace dragonite {

// Bootstraps into the target JVM using pure JNI (URLClassLoader path).
// Does NOT use instrument.dll / Agent_OnAttach — avoids JVMTI agent
// registration entirely, making it invisible to JVM-internal agent
// detection (InjGen pattern scanning on jvm.dll memory).
//
// Flow:
//   1. Wait for jvm.dll to appear in our process (we're injected)
//   2. JNI_GetCreatedJavaVMs → get the live JavaVM*
//   3. AttachCurrentThread → get JNIEnv*
//   4. Build a URLClassLoader pointing at the Dragonite JAR
//   5. Load the bootstrap class and call its entry method
//   6. DetachCurrentThread
//
// jarPath: absolute path to the Dragonite JAR file
// error:   populated on failure
bool bootstrap(const std::wstring& jarPath, std::string& error);

// Directory the payload DLL lives in
std::wstring payloadDir();

// Log to payload.log next to the DLL
void payloadLog(const char* fmt, ...);

} // namespace dragonite
