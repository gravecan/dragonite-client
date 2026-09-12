// Payload DLL — injected into the target Minecraft/Lunar process.
// DllMain spawns a worker thread that reads the JAR path from
// dragonite.cfg and bootstraps into the JVM via pure JNI.

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <string>
#include <fstream>

#include "jvm_bridge.h"

extern "C" void payload_setSelfModule(HMODULE m);

namespace {

HMODULE gMod = nullptr;

std::wstring selfDir() {
    wchar_t buf[MAX_PATH] = {};
    GetModuleFileNameW(gMod, buf, MAX_PATH);
    std::wstring p = buf;
    auto pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

std::wstring readCfg() {
    const wchar_t* names[] = { L"\\boot.ini", L"\\bootstrap.ini", L"\\dragonite.cfg" };
    for (const wchar_t* name : names) {
        std::wstring cfgPath = selfDir() + name;
        std::ifstream f(cfgPath);
        if (!f.is_open()) continue;
        std::string line;
        while (std::getline(f, line)) {
            if (line.empty() || line[0] == '#') continue;
            int wsz = MultiByteToWideChar(CP_UTF8, 0, line.c_str(), (int)line.size(), nullptr, 0);
            std::wstring w(wsz, L'\0');
            MultiByteToWideChar(CP_UTF8, 0, line.c_str(), (int)line.size(), w.data(), wsz);
            return w;
        }
    }
    return L"";
}

DWORD WINAPI worker(LPVOID) {
    using namespace dragonite;

    payloadLog("Payload DLL worker started.");

    // Read JAR path from config
    std::wstring jarPath = readCfg();
    if (jarPath.empty()) {
        payloadLog("ERROR: boot.ini not found or empty next to payload DLL.");
        return 1;
    }

    payloadLog("Config JAR path: %ls", jarPath.c_str());

    std::string err;
    bool ok = bootstrap(jarPath, err);
    if (!ok) {
        payloadLog("Bootstrap FAILED: %s", err.c_str());
    }
    return ok ? 0 : 1;
}

} // namespace

BOOL APIENTRY DllMain(HMODULE mod, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        gMod = mod;
        payload_setSelfModule(mod);
        DisableThreadLibraryCalls(mod);
        HANDLE t = CreateThread(nullptr, 0, worker, nullptr, 0, nullptr);
        if (t) CloseHandle(t);
    }
    return TRUE;
}
