# Chimera Red - Vulkan PBKDF2 Compute Engine
# CMake build configuration for NDK

cmake_minimum_required(VERSION 3.18.1)
project("vulkan_cracker")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Vulkan (provided by Android NDK)
find_package(Vulkan REQUIRED)

# Add the native library
add_library(
    vulkan_cracker
    SHARED
    vulkan_cracker.cpp
)

# Link against Vulkan, Android log, and other required libraries
target_link_libraries(
    vulkan_cracker
    Vulkan::Vulkan
    android
    log
)

# Compiler optimizations for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(vulkan_cracker PRIVATE -O3 -ffast-math)
endif()

# Enable NEON for ARM (additional SIMD optimizations)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(vulkan_cracker PRIVATE -march=armv8-a+simd)
endif()
