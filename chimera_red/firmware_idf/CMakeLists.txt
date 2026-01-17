cmake_minimum_required(VERSION 3.16)

# Set target chip - ESP32-S3
set(IDF_TARGET esp32s3)

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(chimera_red)
