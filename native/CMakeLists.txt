cmake_minimum_required(VERSION 3.22)
project(otjni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(TP "${CMAKE_CURRENT_SOURCE_DIR}/../third_party" ABSOLUTE)

# Keep the build small: the app needs the libraries, not the CLIs/tests/server.
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

# KleidiAI is left ON deliberately. It does not engage on Armv8.2-A dotprod-only
# silicon (see docs/OPTIMIZATION.md O1), but leaving it enabled means the same
# APK does use it on an i8mm/SME device — and it makes our null result honest:
# we measured KleidiAI failing to load, not KleidiAI compiled out.
set(GGML_CPU_KLEIDIAI ON  CACHE BOOL "" FORCE)

# GGML_CPU_KLEIDIAI=ON makes ggml FetchContent the KleidiAI release tarball from
# GitHub at configure time. That download is flaky (we hit repeated
# "REFUSED_STREAM / Failure when receiving data from the peer") and it makes an
# offline build impossible — bad for us on a deadline and worse for a judge
# rebuilding this. The source is vendored under third_party/kleidiai and pointed
# at here, so configure never touches the network.
if(EXISTS "${TP}/kleidiai/kai/kai_common.h")
    set(FETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD "${TP}/kleidiai" CACHE PATH "" FORCE)
    message(STATUS "otjni: using vendored KleidiAI at ${TP}/kleidiai")
endif()
set(GGML_OPENMP       OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE    OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# NO -march flag anywhere. docs/android.md suggests armv8.7a, which emits
# instructions Cortex-A76/A78 (Armv8.2-A) lack -> SIGILL. GGML dispatches on
# runtime CPU detection instead.

add_subdirectory(${TP}/llama.cpp   ${CMAKE_BINARY_DIR}/llama   EXCLUDE_FROM_ALL)
add_subdirectory(${TP}/whisper.cpp ${CMAKE_BINARY_DIR}/whisper EXCLUDE_FROM_ALL)

add_library(otjni SHARED otjni.cpp)

target_include_directories(otjni PRIVATE
    ${TP}/llama.cpp/include
    ${TP}/llama.cpp/ggml/include
    ${TP}/whisper.cpp/include
    ${TP}/whisper.cpp/ggml/include
)

target_link_libraries(otjni PRIVATE llama whisper log android)
