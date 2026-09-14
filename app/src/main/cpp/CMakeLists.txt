# Onyx Engine — نواة التنبؤ الأصلية (C++17 عبر NDK)
cmake_minimum_required(VERSION 3.22.1)
project(onyxengine CXX)

add_library(onyx_engine SHARED
    onyx_jni.cpp
    onyx/utf8.cpp
    onyx/algo.cpp
    onyx/trie.cpp
    onyx/engine.cpp
    onyx/grammar.cpp)

target_include_directories(onyx_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(onyx_engine PRIVATE cxx_std_17)
target_compile_options(onyx_engine PRIVATE -O2 -fvisibility=hidden -Wall)

find_library(log-lib log)
target_link_libraries(onyx_engine ${log-lib})
