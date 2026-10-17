cmake_minimum_required(VERSION 3.18)
project(memhelper)

add_library(memhelper SHARED memhelper.cpp)
target_link_libraries(memhelper log)
