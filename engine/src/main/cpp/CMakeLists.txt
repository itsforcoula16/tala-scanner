cmake_minimum_required(VERSION 3.22.1)
project("tala_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Source files
add_library(tala_native SHARED
    native_pipeline.cpp
    decode/zxing_wrapper.cpp
    decode/code128_decoder.cpp
    decode/qr_decoder.cpp
    preprocess/adaptive_threshold.cpp
    preprocess/barcode_enhancer.cpp
    yolo/yolo_detector.cpp
)

# Include directories
target_include_directories(tala_native PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/decode
    ${CMAKE_SOURCE_DIR}/preprocess
    ${CMAKE_SOURCE_DIR}/yolo
)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# Link libraries
target_link_libraries(tala_native
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
)
