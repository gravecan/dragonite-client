cmake_minimum_required(VERSION 3.20)
project(DragoniteNative LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS OFF)

if(NOT WIN32)
    message(FATAL_ERROR "DragoniteNative targets Windows only.")
endif()

# ---- Injector executable (extracts+injects DLL into target) ---------------
add_executable(dragonite-injector
    src/injector_main.cpp
)
target_include_directories(dragonite-injector PRIVATE src)
target_compile_definitions(dragonite-injector PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(dragonite-injector PRIVATE
    user32 advapi32 psapi)

if(MSVC)
    target_compile_options(dragonite-injector PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (dragonite-injector PRIVATE /DEBUG /SUBSYSTEM:CONSOLE)
endif()

# ---- Payload DLL (runs inside target JVM process) -------------------------
if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR
        "JAVA_HOME is not set. Install a JDK and set JAVA_HOME.")
endif()
string(STRIP "$ENV{JAVA_HOME}" JAVA_HOME_STRIPPED)
set(JAVA_INCLUDE "${JAVA_HOME_STRIPPED}/include")
if(NOT EXISTS "${JAVA_INCLUDE}/jni.h")
    message(FATAL_ERROR "jni.h not found under ${JAVA_INCLUDE}. JAVA_HOME must point at a JDK.")
endif()

add_library(dragonite-payload SHARED
    src/payload_main.cpp
    src/jvm_bridge.cpp
    src/pe_mapper.cpp
)
target_include_directories(dragonite-payload PRIVATE
    src
    "${JAVA_INCLUDE}"
    "${JAVA_INCLUDE}/win32"
)
target_compile_definitions(dragonite-payload PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
target_link_libraries(dragonite-payload PRIVATE user32 psapi)
set_target_properties(dragonite-payload PROPERTIES OUTPUT_NAME "dragonite-payload" PREFIX "")

if(MSVC)
    target_compile_options(dragonite-payload PRIVATE /W4 /permissive- /Zi /utf-8)
    target_link_options   (dragonite-payload PRIVATE /DEBUG)
endif()

# ---- Stage both into dist/ ------------------------------------------------
set(DIST_DIR "${CMAKE_SOURCE_DIR}/dist")
add_custom_command(TARGET dragonite-injector POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${DIST_DIR}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:dragonite-injector>" "${DIST_DIR}/"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:dragonite-payload>" "${DIST_DIR}/"
    COMMENT "Staging dragonite-injector.exe + dragonite-payload.dll into dist/")

# ---- Native bridge DLL (loaded at runtime from JAR via JNI) ----------------
add_library(dragonite-bridge SHARED
    src/native_bridge.cpp
)
target_include_directories(dragonite-bridge PRIVATE
    src
    "${JAVA_INCLUDE}"
    "${JAVA_INCLUDE}/win32"
)
target_compile_definitions(dragonite-bridge PRIVATE
    UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)
set_target_properties(dragonite-bridge PROPERTIES OUTPUT_NAME "dragonite-bridge" PREFIX "")
target_link_libraries(dragonite-bridge PRIVATE bcrypt)

if(MSVC)
    target_compile_options(dragonite-bridge PRIVATE
        /W4 /permissive- /O2 /GL /Gy /Gw /GS /guard:cf /EHsc /utf-8)
    target_link_options(dragonite-bridge PRIVATE
        /RELEASE /LTCG /OPT:REF /OPT:ICF
        /DYNAMICBASE /NXCOMPAT /HIGHENTROPYVA /guard:cf)
endif()

# Copy bridge DLL to resources so Gradle embeds it in the JAR
set(RES_NATIVE "${CMAKE_SOURCE_DIR}/../src/main/resources/native")
add_custom_command(TARGET dragonite-bridge POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory "${RES_NATIVE}"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:dragonite-bridge>" "${RES_NATIVE}/"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        "$<TARGET_FILE:dragonite-bridge>" "${DIST_DIR}/"
    COMMENT "Staging dragonite-bridge.dll into resources/native/ and dist/")
