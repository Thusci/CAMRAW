cmake_minimum_required(VERSION 3.22.1)

project(camraw_gphoto_bridge LANGUAGES C CXX)

set(CAMRAW_ROOT_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../..)
set(CAMRAW_CONFIG_DIR ${CMAKE_CURRENT_LIST_DIR}/config)
set(LIBUSB_ROOT_DIR ${CAMRAW_ROOT_DIR}/third_party/libusb-1.0.29)
set(LIBGPHOTO2_ROOT_DIR ${CAMRAW_ROOT_DIR}/third_party/libgphoto2-2.5.33)
set(LIBTOOL_ROOT_DIR ${CAMRAW_ROOT_DIR}/third_party/libtool-2.4.7)

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

if(EXISTS ${LIBTOOL_ROOT_DIR}/libltdl/ltdl.c)
    set(CAMRAW_HAS_LIBTOOL_SOURCE 1)
else()
    set(CAMRAW_HAS_LIBTOOL_SOURCE 0)
endif()

if(CAMRAW_HAS_LIBUSB_SOURCE AND CAMRAW_HAS_LIBGPHOTO2_SOURCE AND CAMRAW_HAS_LIBTOOL_SOURCE)
    set(CAMRAW_HAS_REAL_GPHOTO_BACKEND 1)
else()
    set(CAMRAW_HAS_REAL_GPHOTO_BACKEND 0)
endif()

find_library(log-lib log)
find_library(dl-lib dl)

if(CAMRAW_HAS_LIBUSB_SOURCE)
    add_library(
        camraw_libusb
        SHARED
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

    target_link_libraries(camraw_libusb PRIVATE ${log-lib})
endif()

if(CAMRAW_HAS_REAL_GPHOTO_BACKEND)
    add_library(
        camraw_ltdl
        SHARED
        ${LIBTOOL_ROOT_DIR}/libltdl/loaders/preopen.c
        ${LIBTOOL_ROOT_DIR}/libltdl/loaders/dlopen.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt__alloc.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt__argz.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt__dirent.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt__strl.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt_dlloader.c
        ${LIBTOOL_ROOT_DIR}/libltdl/lt_error.c
        ${LIBTOOL_ROOT_DIR}/libltdl/ltdl.c
        ${LIBTOOL_ROOT_DIR}/libltdl/slist.c
        libltdl_preloaded.c
    )

    target_include_directories(
        camraw_ltdl
        PUBLIC
        ${LIBTOOL_ROOT_DIR}/libltdl
        PRIVATE
        ${CAMRAW_CONFIG_DIR}/libltdl
        ${LIBTOOL_ROOT_DIR}/libltdl/libltdl
    )

    target_compile_definitions(
        camraw_ltdl
        PRIVATE
        LT_CONFIG_H=<config-ltdl.h>
        LTDL
        LTDLOPEN=libltdl
    )

    target_compile_options(
        camraw_ltdl
        PRIVATE
        -Wno-unused-parameter
    )

    target_link_libraries(camraw_ltdl PRIVATE ${dl-lib})

    add_library(
        camraw_gphoto2_port
        SHARED
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-info-list.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-locking.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-log.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-version.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-portability.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port/gphoto2-port-result.c
    )

    target_include_directories(
        camraw_gphoto2_port
        PUBLIC
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port
        PRIVATE
        ${CAMRAW_CONFIG_DIR}/libgphoto2_port
        ${LIBGPHOTO2_ROOT_DIR}
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libgphoto2_port
    )

    target_compile_definitions(
        camraw_gphoto2_port
        PRIVATE
        _GPHOTO2_INTERNAL_CODE
        IOLIBS="."
        LOCALEDIR="."
    )

    target_compile_options(
        camraw_gphoto2_port
        PRIVATE
        -Wno-unused-parameter
        -Wno-format
    )

    target_link_libraries(
        camraw_gphoto2_port
        PUBLIC
        camraw_ltdl
        PRIVATE
        ${log-lib}
        ${dl-lib}
    )

    add_library(
        camraw_gphoto2
        SHARED
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-abilities-list.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/ahd_bayer.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/bayer.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-camera.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-context.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/exif.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-file.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-filesys.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gamma.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/jpeg.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-list.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-result.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-version.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-setting.c
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2/gphoto2-widget.c
    )

    target_include_directories(
        camraw_gphoto2
        PUBLIC
        ${LIBGPHOTO2_ROOT_DIR}
        PRIVATE
        ${CAMRAW_CONFIG_DIR}/libgphoto2
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2
    )

    target_compile_definitions(
        camraw_gphoto2
        PRIVATE
        _GPHOTO2_INTERNAL_CODE
        CAMLIBS="."
        LOCALEDIR="."
    )

    target_compile_options(
        camraw_gphoto2
        PRIVATE
        -Wno-unused-parameter
        -Wno-format
    )

    target_link_libraries(
        camraw_gphoto2
        PUBLIC
        camraw_gphoto2_port
        camraw_ltdl
        PRIVATE
        m
        ${dl-lib}
    )

    add_library(
        camraw_gphoto2_iolib_usb1
        SHARED
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port/libusb1/libusb1.c
    )

    target_include_directories(
        camraw_gphoto2_iolib_usb1
        PRIVATE
        ${CAMRAW_CONFIG_DIR}/libgphoto2_port
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port
        ${LIBUSB_ROOT_DIR}/libusb
    )

    target_compile_definitions(
        camraw_gphoto2_iolib_usb1
        PRIVATE
        _GPHOTO2_INTERNAL_CODE
        IOLIBS="."
        LOCALEDIR="."
    )

    target_compile_options(
        camraw_gphoto2_iolib_usb1
        PRIVATE
        -Wno-unused-parameter
        -Wno-format
    )

    target_link_libraries(
        camraw_gphoto2_iolib_usb1
        PRIVATE
        camraw_gphoto2_port
        camraw_libusb
        ${log-lib}
    )

    add_library(
        camraw_gphoto2_camlib_ptp2
        SHARED
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/ptp.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/library.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/usb.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/ptpip.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/config.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/olympus-wrap.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/chdk.c
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2/fujiptpip.c
    )

    target_include_directories(
        camraw_gphoto2_camlib_ptp2
        PRIVATE
        ${CAMRAW_CONFIG_DIR}/libgphoto2
        ${LIBGPHOTO2_ROOT_DIR}
        ${LIBGPHOTO2_ROOT_DIR}/libgphoto2_port
        ${LIBGPHOTO2_ROOT_DIR}/camlibs/ptp2
    )

    target_compile_definitions(
        camraw_gphoto2_camlib_ptp2
        PRIVATE
        _GPHOTO2_INTERNAL_CODE
        CAMLIBS="."
        LOCALEDIR="."
    )

    target_compile_options(
        camraw_gphoto2_camlib_ptp2
        PRIVATE
        -Wno-unused-parameter
        -Wno-format
        -Wno-shadow
        -Wno-missing-field-initializers
    )

    target_link_libraries(
        camraw_gphoto2_camlib_ptp2
        PRIVATE
        camraw_gphoto2
        camraw_gphoto2_port
        m
    )
endif()

add_library(
    camraw_gphoto_bridge
    SHARED
    gphoto_jni_bridge.cpp
)

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
    CAMRAW_HAS_LIBTOOL_SOURCE=${CAMRAW_HAS_LIBTOOL_SOURCE}
    CAMRAW_HAS_REAL_GPHOTO_BACKEND=${CAMRAW_HAS_REAL_GPHOTO_BACKEND}
    CAMRAW_LIBUSB_VERSION="1.0.29"
    CAMRAW_LIBGPHOTO2_VERSION="2.5.33"
    CAMRAW_LIBTOOL_VERSION="2.4.7"
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

if(TARGET camraw_gphoto2)
    target_link_libraries(
        camraw_gphoto_bridge
        camraw_gphoto2
        camraw_gphoto2_port
        camraw_ltdl
    )
endif()
