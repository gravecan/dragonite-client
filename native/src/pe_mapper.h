#pragma once
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>

namespace dragonite {

void* manualMap(const unsigned char* rawPe, size_t size);
void* findExport(void* mappedBase, const char* name);
void  eraseMappedHeaders(void* mappedBase);

}
