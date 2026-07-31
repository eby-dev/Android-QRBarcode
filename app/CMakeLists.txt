cmake_minimum_required(VERSION 3.4.1)
project(qrbarcode)

set(CMAKE_ANDROID_ARM_PAGE_SIZE 16384)

add_library(
        native-lib
        SHARED
        src/main/cpp/native-lib.cpp
)
