#include "pe_mapper.h"
#include <cstring>

namespace dragonite {

void* manualMap(const unsigned char* rawPe, size_t size) {
    if (size < sizeof(IMAGE_DOS_HEADER)) return nullptr;
    auto* dos = (const IMAGE_DOS_HEADER*)rawPe;
    if (dos->e_magic != 0x5A4D) return nullptr;
    if ((size_t)dos->e_lfanew + sizeof(IMAGE_NT_HEADERS) > size) return nullptr;

    auto* nt = (const IMAGE_NT_HEADERS*)(rawPe + dos->e_lfanew);
    if (nt->Signature != IMAGE_NT_SIGNATURE) return nullptr;

    SIZE_T imageSize = nt->OptionalHeader.SizeOfImage;
    BYTE* base = (BYTE*)VirtualAlloc(nullptr, imageSize,
        MEM_COMMIT | MEM_RESERVE, PAGE_EXECUTE_READWRITE);
    if (!base) return nullptr;

    memcpy(base, rawPe, nt->OptionalHeader.SizeOfHeaders);

    auto* section = IMAGE_FIRST_SECTION(nt);
    for (WORD i = 0; i < nt->FileHeader.NumberOfSections; i++) {
        if (section[i].SizeOfRawData == 0) continue;
        if (section[i].PointerToRawData + section[i].SizeOfRawData > size) continue;
        memcpy(base + section[i].VirtualAddress,
               rawPe + section[i].PointerToRawData,
               section[i].SizeOfRawData);
    }

    DWORD_PTR delta = (DWORD_PTR)base - nt->OptionalHeader.ImageBase;
    if (delta != 0) {
        auto& relocDir = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_BASERELOC];
        if (relocDir.Size > 0 && relocDir.VirtualAddress > 0) {
            auto* reloc = (IMAGE_BASE_RELOCATION*)(base + relocDir.VirtualAddress);
            auto* relocEnd = (IMAGE_BASE_RELOCATION*)(base + relocDir.VirtualAddress + relocDir.Size);
            while (reloc < relocEnd && reloc->VirtualAddress > 0 && reloc->SizeOfBlock > 0) {
                int count = (reloc->SizeOfBlock - sizeof(IMAGE_BASE_RELOCATION)) / sizeof(WORD);
                WORD* entries = (WORD*)((BYTE*)reloc + sizeof(IMAGE_BASE_RELOCATION));
                for (int j = 0; j < count; j++) {
                    int type = entries[j] >> 12;
                    int offset = entries[j] & 0xFFF;
                    BYTE* target = base + reloc->VirtualAddress + offset;
#ifdef _WIN64
                    if (type == IMAGE_REL_BASED_DIR64) {
                        *(ULONGLONG*)target += delta;
                    }
#endif
                    if (type == IMAGE_REL_BASED_HIGHLOW) {
                        *(DWORD*)target += (DWORD)delta;
                    }
                }
                reloc = (IMAGE_BASE_RELOCATION*)((BYTE*)reloc + reloc->SizeOfBlock);
            }
        }
    }

    auto& importDir = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_IMPORT];
    if (importDir.Size > 0 && importDir.VirtualAddress > 0) {
        auto* imp = (IMAGE_IMPORT_DESCRIPTOR*)(base + importDir.VirtualAddress);
        while (imp->Name) {
            const char* dllName = (const char*)(base + imp->Name);
            HMODULE mod = GetModuleHandleA(dllName);
            if (!mod) mod = LoadLibraryA(dllName);
            if (!mod) {
                VirtualFree(base, 0, MEM_RELEASE);
                return nullptr;
            }

            auto* thunk = (IMAGE_THUNK_DATA*)(base + imp->FirstThunk);
            auto* origThunk = imp->OriginalFirstThunk
                ? (IMAGE_THUNK_DATA*)(base + imp->OriginalFirstThunk)
                : thunk;

            while (origThunk->u1.AddressOfData) {
#ifdef _WIN64
                if (origThunk->u1.Ordinal & IMAGE_ORDINAL_FLAG64) {
                    thunk->u1.Function = (ULONGLONG)GetProcAddress(
                        mod, MAKEINTRESOURCEA(IMAGE_ORDINAL64(origThunk->u1.Ordinal)));
                } else {
#else
                if (origThunk->u1.Ordinal & IMAGE_ORDINAL_FLAG32) {
                    thunk->u1.Function = (ULONGLONG)GetProcAddress(
                        mod, MAKEINTRESOURCEA(IMAGE_ORDINAL32(origThunk->u1.Ordinal)));
                } else {
#endif
                    auto* name = (IMAGE_IMPORT_BY_NAME*)(base + origThunk->u1.AddressOfData);
                    thunk->u1.Function = (ULONGLONG)GetProcAddress(mod, name->Name);
                }
                if (!thunk->u1.Function) {
                    VirtualFree(base, 0, MEM_RELEASE);
                    return nullptr;
                }
                thunk++;
                origThunk++;
            }
            imp++;
        }
    }

    if (nt->OptionalHeader.AddressOfEntryPoint) {
        auto dllMain = (BOOL(APIENTRY*)(HMODULE, DWORD, LPVOID))
            (base + nt->OptionalHeader.AddressOfEntryPoint);
        dllMain((HMODULE)base, DLL_PROCESS_ATTACH, nullptr);
    }

    return base;
}

void* findExport(void* mappedBase, const char* name) {
    if (!mappedBase || !name) return nullptr;
    auto* base = (BYTE*)mappedBase;
    auto* dos = (IMAGE_DOS_HEADER*)base;
    auto* nt = (IMAGE_NT_HEADERS*)(base + dos->e_lfanew);
    auto& exportDir = nt->OptionalHeader.DataDirectory[IMAGE_DIRECTORY_ENTRY_EXPORT];
    if (exportDir.Size == 0 || exportDir.VirtualAddress == 0) return nullptr;

    auto* exports = (IMAGE_EXPORT_DIRECTORY*)(base + exportDir.VirtualAddress);
    auto* names = (DWORD*)(base + exports->AddressOfNames);
    auto* ordinals = (WORD*)(base + exports->AddressOfNameOrdinals);
    auto* functions = (DWORD*)(base + exports->AddressOfFunctions);

    for (DWORD i = 0; i < exports->NumberOfNames; i++) {
        if (strcmp((const char*)(base + names[i]), name) == 0) {
            return (void*)(base + functions[ordinals[i]]);
        }
    }
    return nullptr;
}

void eraseMappedHeaders(void* mappedBase) {
    if (!mappedBase) return;
    SecureZeroMemory(mappedBase, 4096);
}

}
