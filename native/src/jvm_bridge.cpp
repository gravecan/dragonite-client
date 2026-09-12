// JVM Bridge — URLClassLoader-only bootstrap (InjGen-safe)
//
// This is the core of the bypass. We attach to the running JVM using
// JNI_GetCreatedJavaVMs (exported by jvm.dll), NOT through instrument.dll
// or Agent_OnAttach. This means:
//   - No JVMTI environment is created
//   - No agent attach state is written into jvm.dll's memory
//   - InjGen's pattern scan on jvm.dll memory finds nothing
//   - The JVM doesn't know an "agent" was loaded — we just loaded a class
//
// Trade-off: no java.lang.instrument.Instrumentation object. If Dragonite
// needs retransformClasses(), that has to go through a different path.

#include "jvm_bridge.h"
#include "pe_mapper.h"

#include <cstdarg>
#include <cstdio>
#include <string>
#include <fstream>
#include <intrin.h>

namespace dragonite {

namespace {

HMODULE gSelfModule = nullptr;
HANDLE  gLogFile    = INVALID_HANDLE_VALUE;
CRITICAL_SECTION gLogLock;
bool gLogInit = false;

void ensureLogInit() {
    if (gLogInit) return;
    InitializeCriticalSection(&gLogLock);
    gLogFile = INVALID_HANDLE_VALUE;
    gLogInit = true;
}

std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return {};
    int sz = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                                  nullptr, 0, nullptr, nullptr);
    std::string out(sz, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(),
                        out.data(), sz, nullptr, nullptr);
    return out;
}

typedef jint (JNICALL *PFN_JNI_GetCreatedJavaVMs)(JavaVM**, jsize, jsize*);

// Poll for jvm.dll to be loaded (it might not be ready yet when we're
// injected early). Timeout 60s to handle slow Lunar startup.
PFN_JNI_GetCreatedJavaVMs findJvmEntry(int timeoutMs = 60000) {
    DWORD start = GetTickCount();
    while ((GetTickCount() - start) < (DWORD)timeoutMs) {
        HMODULE jvm = GetModuleHandleW(L"jvm.dll");
        if (jvm) {
            auto fn = (PFN_JNI_GetCreatedJavaVMs)GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
            if (fn) return fn;
        }
        Sleep(200);
    }
    return nullptr;
}

void describeException(JNIEnv* env, const char* where) {
    if (!env->ExceptionCheck()) return;
    jthrowable t = env->ExceptionOccurred();
    env->ExceptionClear();
    payloadLog("[%s] Java exception:", where);
    if (!t) return;
    jclass tc = env->GetObjectClass(t);
    jmethodID toString = env->GetMethodID(tc, "toString", "()Ljava/lang/String;");
    if (toString) {
        auto js = (jstring)env->CallObjectMethod(t, toString);
        if (js) {
            const char* s = env->GetStringUTFChars(js, nullptr);
            payloadLog("  %s", s ? s : "(null)");
            env->ReleaseStringUTFChars(js, s);
        }
    }
    env->DeleteLocalRef(t);
}

// Read the JAR path from bootstrap.ini next to the DLL
std::wstring readConfigJarPath() {
    std::wstring dir = payloadDir();
    std::wstring cfgPath = dir + L"\\boot.ini";

    // Read as UTF-8
    std::ifstream f(cfgPath);
    if (!f.is_open()) {
        // Legacy names from older extracts
        cfgPath = dir + L"\\bootstrap.ini";
        f.open(cfgPath);
        if (!f.is_open()) {
            cfgPath = dir + L"\\dragonite.cfg";
            f.open(cfgPath);
            if (!f.is_open()) return L"";
        }
    }
    std::string line;
    while (std::getline(f, line)) {
        // Skip empty lines and comments
        if (line.empty() || line[0] == '#') continue;
        // Convert to wide
        int wsz = MultiByteToWideChar(CP_UTF8, 0, line.c_str(), (int)line.size(), nullptr, 0);
        std::wstring w(wsz, L'\0');
        MultiByteToWideChar(CP_UTF8, 0, line.c_str(), (int)line.size(), w.data(), wsz);
        return w;
    }
    return L"";
}

bool memoryMapBridge(JNIEnv* env, JavaVM* vm, const std::string& jarUtf8) {
    payloadLog("Memory-mapping bridge DLL from JAR...");

    jclass jarFileClass = env->FindClass("java/util/jar/JarFile");
    if (!jarFileClass) { env->ExceptionClear(); return false; }
    jmethodID jarFileCtor = env->GetMethodID(jarFileClass, "<init>", "(Ljava/lang/String;)V");
    jmethodID getEntry = env->GetMethodID(jarFileClass, "getEntry", "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;");
    jmethodID getInputStream = env->GetMethodID(jarFileClass, "getInputStream", "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;");
    jmethodID jarClose = env->GetMethodID(jarFileClass, "close", "()V");

    jstring jJarPath = env->NewStringUTF(jarUtf8.c_str());
    jobject jarFile = env->NewObject(jarFileClass, jarFileCtor, jJarPath);
    if (!jarFile || env->ExceptionCheck()) {
        env->ExceptionClear();
        payloadLog("  Failed to open JAR as JarFile");
        return false;
    }

    jstring entryName = env->NewStringUTF("native/dragonite-bridge.dll");
    jobject entry = env->CallObjectMethod(jarFile, getEntry, entryName);
    if (!entry || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->CallVoidMethod(jarFile, jarClose);
        payloadLog("  Bridge DLL entry not found in JAR");
        return false;
    }

    jobject inputStream = env->CallObjectMethod(jarFile, getInputStream, entry);
    if (!inputStream || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->CallVoidMethod(jarFile, jarClose);
        payloadLog("  Failed to get InputStream for bridge DLL");
        return false;
    }

    jclass isClass = env->FindClass("java/io/InputStream");
    jmethodID readAll = env->GetMethodID(isClass, "readAllBytes", "()[B");
    auto bytesArray = (jbyteArray)env->CallObjectMethod(inputStream, readAll);
    if (!bytesArray || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->CallVoidMethod(jarFile, jarClose);
        payloadLog("  Failed to read bridge DLL bytes");
        return false;
    }

    jsize dllSize = env->GetArrayLength(bytesArray);
    jbyte* dllBytes = env->GetByteArrayElements(bytesArray, nullptr);
    if (!dllBytes || dllSize < 64) {
        if (dllBytes) env->ReleaseByteArrayElements(bytesArray, dllBytes, JNI_ABORT);
        env->CallVoidMethod(jarFile, jarClose);
        payloadLog("  Bridge DLL too small: %d bytes", (int)dllSize);
        return false;
    }
    payloadLog("  Read %d bytes from JAR resource", (int)dllSize);

    void* mapped = dragonite::manualMap(
        (const unsigned char*)dllBytes, (size_t)dllSize);
    env->ReleaseByteArrayElements(bytesArray, dllBytes, JNI_ABORT);
    env->CallVoidMethod(jarFile, jarClose);
    if (env->ExceptionCheck()) env->ExceptionClear();

    if (!mapped) {
        payloadLog("  PE manual map failed");
        return false;
    }
    payloadLog("  PE mapped at %p", mapped);

    typedef jint(JNICALL* JNI_OnLoad_t)(JavaVM*, void*);
    auto onLoad = (JNI_OnLoad_t)dragonite::findExport(mapped, "JNI_OnLoad");
    if (!onLoad) {
        payloadLog("  JNI_OnLoad export not found in mapped PE");
        return false;
    }

    jint result = onLoad(vm, nullptr);
    if (result < 0) {
        payloadLog("  JNI_OnLoad returned error: %d", (int)result);
        return false;
    }
    payloadLog("  JNI_OnLoad returned: %d", (int)result);

    dragonite::eraseMappedHeaders(mapped);
    payloadLog("  Bridge DLL loaded in-memory (no file, no PEB entry)");
    return true;
}

} // namespace

// Forward declarations (defined below bootstrap)
static void scrubJvmFingerprints();
static void unlinkSelfFromPEB();

extern "C" void payload_setSelfModule(HMODULE m) {
    gSelfModule = m;
    ensureLogInit();
}

std::wstring payloadDir() {
    wchar_t buf[MAX_PATH] = {};
    GetModuleFileNameW(gSelfModule, buf, MAX_PATH);
    std::wstring p = buf;
    auto pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

void payloadLog(const char* fmt, ...) {
    ensureLogInit();

    // Debug output only — never create dragonite-payload.log on disk.
    char buf[4096];
    va_list ap; va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf) - 16, fmt, ap);
    va_end(ap);
    if (n < 0) n = 0;

    SYSTEMTIME st; GetLocalTime(&st);
    char header[64];
    sprintf_s(header, "[%02d:%02d:%02d.%03d] ", st.wHour, st.wMinute, st.wSecond, st.wMilliseconds);

    EnterCriticalSection(&gLogLock);
    OutputDebugStringA(header);
    OutputDebugStringA(buf);
    OutputDebugStringA("\n");
    LeaveCriticalSection(&gLogLock);
}

// ---- The main bootstrap — pure JNI, no JVMTI, no instrument.dll ----------
bool bootstrap(const std::wstring& jarPath, std::string& error) {
    payloadLog("=== Dragonite native bootstrap (URLClassLoader path) ===");
    payloadLog("JAR: %s", wideToUtf8(jarPath).c_str());

    // Verify JAR exists
    if (GetFileAttributesW(jarPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        error = "JAR not found: " + wideToUtf8(jarPath);
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }

    // Wait for JVM
    auto getVms = findJvmEntry(60000);
    if (!getVms) {
        error = "jvm.dll did not appear within 60s";
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    payloadLog("Resolved JNI_GetCreatedJavaVMs.");

    // Get the running JVM
    JavaVM* vms[8]; jsize count = 0;
    jint rc = getVms(vms, 8, &count);
    if (rc != JNI_OK || count == 0) {
        error = "JNI_GetCreatedJavaVMs returned no VM";
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    JavaVM* vm = vms[0];
    payloadLog("Found %d JVM(s), using first.", (int)count);

    // Attach our thread to the JVM
    JNIEnv* env = nullptr;
    jint ar = vm->AttachCurrentThread((void**)&env, nullptr);
    if (ar != JNI_OK || !env) {
        error = "AttachCurrentThread failed: rc=" + std::to_string(ar);
        payloadLog("ERROR: %s", error.c_str());
        return false;
    }
    payloadLog("Attached to JVM (JNIEnv=%p).", (void*)env);

    // ---- Build URLClassLoader for the Dragonite JAR ----
    // Convert jar path to file:/// URI
    std::wstring uri = L"file:///";
    for (wchar_t c : jarPath) uri += (c == L'\\') ? L'/' : c;
    std::string uri8 = wideToUtf8(uri);
    payloadLog("JAR URI: %s", uri8.c_str());

    // java.net.URL urlObj = new URL(uri);
    jclass urlClass = env->FindClass("java/net/URL");
    if (!urlClass) {
        describeException(env, "FindClass URL");
        error = "FindClass java/net/URL failed";
        vm->DetachCurrentThread();
        return false;
    }
    jmethodID urlCtor = env->GetMethodID(urlClass, "<init>", "(Ljava/lang/String;)V");
    jstring jUri = env->NewStringUTF(uri8.c_str());
    jobject urlObj = env->NewObject(urlClass, urlCtor, jUri);
    if (!urlObj || env->ExceptionCheck()) {
        describeException(env, "new URL");
        error = "Could not create java.net.URL";
        vm->DetachCurrentThread();
        return false;
    }

    // URL[] urls = { urlObj };
    jobjectArray urls = env->NewObjectArray(1, urlClass, urlObj);

    // URLClassLoader loader = new URLClassLoader(urls);
    jclass loaderClass = env->FindClass("java/net/URLClassLoader");
    jmethodID loaderCtor = env->GetMethodID(loaderClass, "<init>", "([Ljava/net/URL;)V");
    jobject loader = env->NewObject(loaderClass, loaderCtor, urls);
    if (!loader || env->ExceptionCheck()) {
        describeException(env, "new URLClassLoader");
        error = "Could not create URLClassLoader";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("URLClassLoader created for JAR.");

    std::string jarUtf8 = wideToUtf8(jarPath);
    bool bridgeMapped = memoryMapBridge(env, vm, jarUtf8);
    if (bridgeMapped) {
        payloadLog("Bridge DLL memory-mapped successfully (zero disk trace).");
    } else {
        payloadLog("Bridge DLL memory-map failed; Java-side will fall back to System.load().");
    }

    jmethodID loadClassMethod = env->GetMethodID(loaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring className = env->NewStringUTF("me.shedaniel.clothconfig2.injection.bridge.DragoniteAgent");
    auto agentClass = (jclass)env->CallObjectMethod(loader, loadClassMethod, className);
    if (!agentClass || env->ExceptionCheck()) {
        describeException(env, "loadClass DragoniteAgent");
        error = "Could not load DragoniteAgent from JAR";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("Loaded DragoniteAgent class.");

    // DragoniteAgent.nativeEntry(jarPath);
    jmethodID entryMethod = env->GetStaticMethodID(agentClass, "nativeEntry", "(Ljava/lang/String;)V");
    if (!entryMethod) {
        describeException(env, "GetStaticMethodID nativeEntry");
        error = "DragoniteAgent.nativeEntry(String) not found";
        vm->DetachCurrentThread();
        return false;
    }

    jstring jJarPath = env->NewStringUTF(jarUtf8.c_str());
    env->CallStaticVoidMethod(agentClass, entryMethod, jJarPath);
    if (env->ExceptionCheck()) {
        describeException(env, "DragoniteAgent.nativeEntry");
        error = "DragoniteAgent.nativeEntry() threw an exception";
        vm->DetachCurrentThread();
        return false;
    }
    payloadLog("DragoniteAgent.nativeEntry() returned OK.");

    auto zeroJString = [&](jstring js) {
        if (!js) return;
        jsize len = env->GetStringLength(js);
        if (len <= 0) return;
        jclass stringClass = env->FindClass("java/lang/String");
        jfieldID valueField = env->GetFieldID(stringClass, "value", "[B");
        if (!valueField) { env->ExceptionClear(); return; }
        auto arr = (jbyteArray)env->GetObjectField(js, valueField);
        if (!arr) return;
        jsize arrLen = env->GetArrayLength(arr);
        std::vector<jbyte> zeros(arrLen, 0);
        env->SetByteArrayRegion(arr, 0, arrLen, zeros.data());
    };

    zeroJString(jUri);

    vm->DetachCurrentThread();

    scrubJvmFingerprints();

    SecureZeroMemory((void*)uri8.data(), uri8.size());

    payloadLog("=== Bootstrap complete ===");
    return true;
}

// ---- InjGen 2.0 bypass: scrub jvm.dll memory fingerprints ----
//
// InjGen scans jvm.dll's PAGE_READWRITE memory for two DWORD patterns:
//   0x00080006 (524294)  — JVM internal attach/thread state tag
//   0xFCE01E99 (4242546329) — JVM internal state marker
// If both are found AND "com/lunarclient" is in process memory → flagged.
//
// We zero these values in jvm.dll's RW pages after bootstrap.
// These are bookkeeping values left by AttachCurrentThread/DetachCurrentThread,
// not critical for JVM operation post-attach.

static void scrubJvmFingerprints() {
    payloadLog("Scrubbing JVM fingerprints from jvm.dll RW pages...");

    HMODULE jvm = GetModuleHandleW(L"jvm.dll");
    if (!jvm) {
        payloadLog("  jvm.dll not found in module list — skipping.");
        return;
    }

    // Parse PE headers to get image size
    auto* dos = (IMAGE_DOS_HEADER*)jvm;
    if (dos->e_magic != 0x5A4D) return;
    auto* nt = (IMAGE_NT_HEADERS*)((BYTE*)jvm + dos->e_lfanew);
    DWORD imageSize = nt->OptionalHeader.SizeOfImage;

    BYTE* base = (BYTE*)jvm;
    BYTE* end = base + imageSize;

    const DWORD targets[] = { 0x00080006, 0xFCE01E99 };
    int scrubbed[2] = { 0, 0 };

    MEMORY_BASIC_INFORMATION mbi = {};
    BYTE* addr = base;
    while (addr < end) {
        if (VirtualQuery(addr, &mbi, sizeof(mbi)) == 0) break;

        // Only touch RW committed pages (same filter InjGen uses)
        if (mbi.State == MEM_COMMIT &&
            (mbi.Protect & PAGE_READWRITE) &&
            !(mbi.Protect & (PAGE_GUARD | PAGE_NOACCESS))) {

            BYTE* pageStart = (BYTE*)mbi.BaseAddress;
            SIZE_T pageSize = mbi.RegionSize;

            // Scan for target DWORDs and zero them
            for (SIZE_T i = 0; i + sizeof(DWORD) <= pageSize; i += sizeof(DWORD)) {
                DWORD* ptr = (DWORD*)(pageStart + i);
                for (int t = 0; t < 2; t++) {
                    if (*ptr == targets[t]) {
                        *ptr = 0;
                        scrubbed[t]++;
                    }
                }
            }
        }

        addr = (BYTE*)mbi.BaseAddress + mbi.RegionSize;
    }

    payloadLog("  Scrubbed: pattern1(0x80006)=%d, pattern2(0xFCE01E99)=%d",
               scrubbed[0], scrubbed[1]);

    // Also unlink our DLL from PEB module list to hide from EnumProcessModules
    // (InjGen's __vld_pattern doesn't check modules, but defense in depth)
    unlinkSelfFromPEB();
}

// Unlink this DLL from the PEB's InLoadOrderModuleList so it doesn't appear
// in EnumProcessModules. The DLL remains loaded (code pages still mapped)
// but is invisible to module enumeration.
static void unlinkSelfFromPEB() {
    if (!gSelfModule) return;

    typedef struct _PEB_LDR_DATA_MINI {
        ULONG Length;
        BOOLEAN Initialized;
        PVOID SsHandle;
        LIST_ENTRY InLoadOrderModuleList;
        LIST_ENTRY InMemoryOrderModuleList;
        LIST_ENTRY InInitializationOrderModuleList;
    } PEB_LDR_DATA_MINI;

    typedef struct _LDR_DATA_TABLE_ENTRY_MINI {
        LIST_ENTRY InLoadOrderLinks;
        LIST_ENTRY InMemoryOrderLinks;
        LIST_ENTRY InInitializationOrderLinks;
        PVOID DllBase;
    } LDR_DATA_TABLE_ENTRY_MINI;

#if defined(_M_X64) || defined(__x86_64__)
    auto peb = (BYTE*)__readgsqword(0x60);
#else
    auto peb = (BYTE*)__readfsdword(0x30);
#endif
    if (!peb) return;

    auto* ldr = *(PEB_LDR_DATA_MINI**)(peb + 0x18);
    if (!ldr) return;

    LIST_ENTRY* head = &ldr->InLoadOrderModuleList;
    LIST_ENTRY* cur = head->Flink;
    while (cur != head) {
        auto* entry = CONTAINING_RECORD(cur, LDR_DATA_TABLE_ENTRY_MINI, InLoadOrderLinks);
        LIST_ENTRY* next = cur->Flink;
        if (entry->DllBase == (PVOID)gSelfModule) {
            // Unlink from all three lists
            entry->InLoadOrderLinks.Blink->Flink = entry->InLoadOrderLinks.Flink;
            entry->InLoadOrderLinks.Flink->Blink = entry->InLoadOrderLinks.Blink;
            entry->InMemoryOrderLinks.Blink->Flink = entry->InMemoryOrderLinks.Flink;
            entry->InMemoryOrderLinks.Flink->Blink = entry->InMemoryOrderLinks.Blink;
            entry->InInitializationOrderLinks.Blink->Flink = entry->InInitializationOrderLinks.Flink;
            entry->InInitializationOrderLinks.Flink->Blink = entry->InInitializationOrderLinks.Blink;
            payloadLog("  DLL unlinked from PEB module list.");
            break;
        }
        cur = next;
    }
}

} // namespace dragonite


