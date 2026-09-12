// dragonite-injector.exe
// Usage: dragonite-injector.exe <PID> <absolute-path-to-payload.dll>
//
// Performs CreateRemoteThread + LoadLibraryW injection of the payload DLL
// into the target process. The Java side extracts this exe and the DLL from
// the JAR, then launches this as a subprocess.

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <psapi.h>
#include <cstdio>
#include <cstdlib>
#include <string>

#pragma comment(lib, "psapi.lib")

static std::string lastErr() {
    DWORD e = GetLastError();
    char buf[512];
    FormatMessageA(FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                   nullptr, e, 0, buf, sizeof(buf), nullptr);
    return std::string(buf);
}

static bool sameArch(HANDLE hProc) {
    BOOL targetWow = FALSE, selfWow = FALSE;
    IsWow64Process(hProc, &targetWow);
    IsWow64Process(GetCurrentProcess(), &selfWow);
    return (!targetWow) == (!selfWow);
}

static bool verifyLoaded(DWORD pid, const std::wstring& dllPath) {
    HANDLE h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
    if (!h) return false;
    HMODULE mods[2048]; DWORD needed = 0;
    bool found = false;
    if (EnumProcessModulesEx(h, mods, sizeof(mods), &needed, LIST_MODULES_ALL)) {
        DWORD count = needed / sizeof(HMODULE);
        for (DWORD i = 0; i < count; ++i) {
            wchar_t fn[MAX_PATH] = {};
            if (GetModuleFileNameExW(h, mods[i], fn, MAX_PATH)) {
                if (_wcsicmp(fn, dllPath.c_str()) == 0) { found = true; break; }
            }
        }
    }
    CloseHandle(h);
    return found;
}

int wmain(int argc, wchar_t* argv[]) {
    if (argc < 3) {
        fprintf(stderr, "Usage: dragonite-injector.exe <PID> <payload.dll path>\n");
        return 1;
    }

    DWORD pid = (DWORD)_wtoi(argv[1]);
    std::wstring dllPath = argv[2];

    printf("[injector] Target PID: %lu\n", pid);
    printf("[injector] Payload: %ls\n", dllPath.c_str());

    // Verify DLL exists
    if (GetFileAttributesW(dllPath.c_str()) == INVALID_FILE_ATTRIBUTES) {
        fprintf(stderr, "[injector] ERROR: DLL not found: %ls\n", dllPath.c_str());
        return 2;
    }

    // Open target process
    const DWORD access = PROCESS_CREATE_THREAD | PROCESS_QUERY_INFORMATION |
                         PROCESS_VM_OPERATION | PROCESS_VM_WRITE | PROCESS_VM_READ;
    HANDLE hProc = OpenProcess(access, FALSE, pid);
    if (!hProc) {
        fprintf(stderr, "[injector] ERROR: OpenProcess failed (run as admin?): %s\n", lastErr().c_str());
        return 3;
    }

    // Architecture check
    if (!sameArch(hProc)) {
        fprintf(stderr, "[injector] ERROR: Architecture mismatch (injector vs target)\n");
        CloseHandle(hProc);
        return 4;
    }

    // Allocate memory in target for DLL path
    SIZE_T pathBytes = (dllPath.size() + 1) * sizeof(wchar_t);
    void* remoteMem = VirtualAllocEx(hProc, nullptr, pathBytes, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
    if (!remoteMem) {
        fprintf(stderr, "[injector] ERROR: VirtualAllocEx failed: %s\n", lastErr().c_str());
        CloseHandle(hProc);
        return 5;
    }

    // Write DLL path into target
    if (!WriteProcessMemory(hProc, remoteMem, dllPath.c_str(), pathBytes, nullptr)) {
        fprintf(stderr, "[injector] ERROR: WriteProcessMemory failed: %s\n", lastErr().c_str());
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return 6;
    }

    // Resolve LoadLibraryW
    HMODULE k32 = GetModuleHandleW(L"kernel32.dll");
    auto loadLib = (LPTHREAD_START_ROUTINE)GetProcAddress(k32, "LoadLibraryW");
    if (!loadLib) {
        fprintf(stderr, "[injector] ERROR: GetProcAddress(LoadLibraryW) failed\n");
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return 7;
    }

    // Create remote thread
    HANDLE thread = CreateRemoteThread(hProc, nullptr, 0, loadLib, remoteMem, 0, nullptr);
    if (!thread) {
        fprintf(stderr, "[injector] ERROR: CreateRemoteThread failed: %s\n", lastErr().c_str());
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return 8;
    }

    printf("[injector] Remote thread created, waiting...\n");
    DWORD wait = WaitForSingleObject(thread, 30000);
    if (wait != WAIT_OBJECT_0) {
        fprintf(stderr, "[injector] ERROR: Remote thread timeout (status=%lu)\n", wait);
        CloseHandle(thread);
        VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);
        CloseHandle(hProc);
        return 9;
    }

    DWORD exitCode = 0;
    GetExitCodeThread(thread, &exitCode);
    CloseHandle(thread);
    VirtualFreeEx(hProc, remoteMem, 0, MEM_RELEASE);

    if (exitCode == 0) {
        fprintf(stderr, "[injector] ERROR: LoadLibraryW returned NULL in target\n");
        CloseHandle(hProc);
        return 10;
    }

    printf("[injector] LoadLibraryW returned handle 0x%llX\n", (unsigned long long)exitCode);

    // Verify module is loaded
    Sleep(500);
    if (verifyLoaded(pid, dllPath)) {
        printf("[injector] SUCCESS: Payload verified in target module list\n");
    } else {
        printf("[injector] WARNING: Payload not found in module list (may still be OK)\n");
    }

    CloseHandle(hProc);
    return 0;
}
