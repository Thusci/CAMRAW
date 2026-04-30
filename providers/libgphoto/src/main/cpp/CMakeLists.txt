cmake_minimum_required(VERSION 3.22.1)

project(camraw_gphoto_bridge LANGUAGES C CXX)

set(CAMRAW_ROOT_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../..)
set(LIBUSB_ROOT_DIR ${CAMRAW_ROOT_DIR}/third_party/libusb-1.0.29)
set(LIBGPHOTO2_ROOT_DIR ${CAMRAW_ROOT_DIR}/third_party/libgphoto2-2.5.33)

if(EXISTS ${LIBUSB_ROOT_DIR}/libusb/libusb.h)
    set(CAMRAW_HAS_LIBUSB_SOURCE 1)
else()
    set(CAMRAW_HAS_LIBUSB_SOURCE 0)
endif()

if(EXISTS ${LIBGPHOTO2_ROOT_DIR}/gphoto2/gphoto2-camera.h)
    set(CAMRAW_HAS_LIBGPHOTO2_SOURCE 1)
else()
    set(CAMRAW_HAS_LIBGPHOTO2_SOURCE 0)
endif()

if(CAMRAW_HAS_LIBUSB_SOURCE)
    add_library(
        camraw_libusb
        STATIC
        ${LIBUSB_ROOT_DIR}/libusb/core.c
        ${LIBUSB_ROOT_DIR}/libusb/descriptor.c
        ${LIBUSB_ROOT_DIR}/libusb/hotplug.c
        ${LIBUSB_ROOT_DIR}/libusb/io.c
        ${LIBUSB_ROOT_DIR}/libusb/sync.c
        ${LIBUSB_ROOT_DIR}/libusb/strerror.c
        ${LIBUSB_ROOT_DIR}/libusb/os/linux_usbfs.c
        ${LIBUSB_ROOT_DIR}/libusb/os/events_posix.c
        ${LIBUSB_ROOT_DIR}/libusb/os/threads_posix.c
        ${LIBUSB_ROOT_DIR}/libusb/os/linux_netlink.c
    )

    target_include_directories(
        camraw_libusb
        PUBLIC
        ${LIBUSB_ROOT_DIR}/libusb
        PRIVATE
        ${LIBUSB_ROOT_DIR}/android
        ${LIBUSB_ROOT_DIR}/libusb/os
    )

    target_compile_options(
        camraw_libusb
        PRIVATE
        -fvisibility=hidden
        -pthread
        -Wno-unused-parameter
    )

    target_link_libraries(camraw_libusb PRIVATE log)
endif()

add_library(
    camraw_gphoto_bridge
    SHARED
    gphoto_jni_bridge.cpp
)

find_library(log-lib log)

target_compile_features(camraw_gphoto_bridge PRIVATE cxx_std_17)

target_include_directories(
    camraw_gphoto_bridge
    PRIVATE
    ${LIBUSB_ROOT_DIR}/libusb
    ${LIBGPHOTO2_ROOT_DIR}
    ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port
)

target_compile_definitions(
    camraw_gphoto_bridge
    PRIVATE
    CAMRAW_HAS_LIBUSB_SOURCE=${CAMRAW_HAS_LIBUSB_SOURCE}
    CAMRAW_HAS_LIBGPHOTO2_SOURCE=${CAMRAW_HAS_LIBGPHOTO2_SOURCE}
    CAMRAW_LIBUSB_VERSION="1.0.29"
    CAMRAW_LIBGPHOTO2_VERSION="2.5.33"
)

target_compile_options(
    camraw_gphoto_bridge
    PRIVATE
    -Wall
    -Wextra
    -Werror
)

target_link_libraries(
    camraw_gphoto_bridge
    ${log-lib}
)

if(TARGET camraw_libusb)
    target_link_libraries(camraw_gphoto_bridge camraw_libusb)
endif()
