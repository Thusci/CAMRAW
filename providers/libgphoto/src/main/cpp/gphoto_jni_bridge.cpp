#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#if CAMRAW_HAS_LIBUSB_SOURCE
#include <libusb.h>
#endif

#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>

namespace {

constexpr const char* kTag = "CAMRAW_GPHOTO";

struct NativeHandle {
    int owned_fd = -1;
    int vendor_id = 0;
    int product_id = 0;
    bool libusb_initialized = false;
    bool libusb_wrapped = false;
    int libusb_init_code = 0;
    int libusb_wrap_code = 0;
    std::string backend_state = "NATIVE_BACKEND_UNSUPPORTED";
#if CAMRAW_HAS_LIBUSB_SOURCE
    libusb_context* usb_context = nullptr;
    libusb_device_handle* usb_handle = nullptr;
#endif
};

std::mutex g_mutex;
std::unordered_map<jlong, NativeHandle> g_handles;
jlong g_next_handle = 1;

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

std::string escape_json(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size() + 8);
    for (const char c : value) {
        switch (c) {
            case '\\': escaped += "\\\\"; break;
            case '"': escaped += "\\\""; break;
            case '\n': escaped += "\\n"; break;
            case '\r': escaped += "\\r"; break;
            case '\t': escaped += "\\t"; break;
            default: escaped += c; break;
        }
    }
    return escaped;
}

std::string json_pair(const char* key, const std::string& value) {
    return std::string("\"") + key + "\":\"" + escape_json(value) + "\"";
}

NativeHandle* find_handle(jlong handle) {
    auto iterator = g_handles.find(handle);
    if (iterator == g_handles.end()) {
        return nullptr;
    }
    return &iterator->second;
}

std::string backend_name() {
#if CAMRAW_HAS_LIBUSB_SOURCE
    return "libusb-fd-bridge";
#else
    return "stub";
#endif
}

std::string source_state_json() {
    return std::string("\"libusbSource\":") + (CAMRAW_HAS_LIBUSB_SOURCE ? "true" : "false") + ","
        + "\"libgphoto2Source\":" + (CAMRAW_HAS_LIBGPHOTO2_SOURCE ? "true" : "false") + ","
        + json_pair("libusbVersion", CAMRAW_HAS_LIBUSB_SOURCE ? CAMRAW_LIBUSB_VERSION : "missing") + ","
        + json_pair("libgphoto2Version", CAMRAW_HAS_LIBGPHOTO2_SOURCE ? CAMRAW_LIBGPHOTO2_VERSION : "missing");
}

std::string unsupported_json(const NativeHandle* handle, const std::string& operation) {
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("error", "NATIVE_BACKEND_UNSUPPORTED") << ","
        << json_pair("operation", operation) << ","
        << json_pair("backend", backend_name()) << ","
        << source_state_json() << ","
        << "\"fdBridge\":\"dup\",";
    if (handle != nullptr) {
        stream
            << "\"fdState\":\"" << (handle->owned_fd >= 0 ? "open" : "closed") << "\","
            << "\"libusbInitialized\":" << (handle->libusb_initialized ? "true" : "false") << ","
            << "\"libusbWrapped\":" << (handle->libusb_wrapped ? "true" : "false") << ","
            << "\"libusbInitCode\":" << handle->libusb_init_code << ","
            << "\"libusbWrapCode\":" << handle->libusb_wrap_code << ","
            << "\"vendorId\":" << handle->vendor_id << ","
            << "\"productId\":" << handle->product_id << ",";
    }
    stream << json_pair(
        "message",
        "libusb fd bridge is compiled, but libgphoto2 camera backend/camlibs are not initialized yet"
    ) << "}";
    return stream.str();
}

std::string invalid_handle_json(const std::string& operation) {
    return std::string("{")
        + json_pair("error", "NATIVE_HANDLE_INVALID") + ","
        + json_pair("operation", operation) + ","
        + json_pair("backend", backend_name()) + ","
        + source_state_json()
        + "}";
}

void cleanup_handle(NativeHandle& handle) {
#if CAMRAW_HAS_LIBUSB_SOURCE
    if (handle.usb_handle != nullptr) {
        libusb_close(handle.usb_handle);
        handle.usb_handle = nullptr;
    }
    if (handle.usb_context != nullptr) {
        libusb_exit(handle.usb_context);
        handle.usb_context = nullptr;
    }
#endif
    if (handle.owned_fd >= 0) {
        close(handle.owned_fd);
        handle.owned_fd = -1;
    }
}

void init_libusb_from_fd(NativeHandle& handle) {
#if CAMRAW_HAS_LIBUSB_SOURCE
    handle.libusb_init_code = libusb_init(&handle.usb_context);
    handle.libusb_initialized = handle.libusb_init_code == 0 && handle.usb_context != nullptr;
    if (!handle.libusb_initialized) {
        handle.backend_state = "NATIVE_BACKEND_INIT_FAILED";
        return;
    }

    handle.libusb_wrap_code = libusb_wrap_sys_device(
        handle.usb_context,
        static_cast<intptr_t>(handle.owned_fd),
        &handle.usb_handle
    );
    handle.libusb_wrapped = handle.libusb_wrap_code == 0 && handle.usb_handle != nullptr;
    handle.backend_state = handle.libusb_wrapped ? "LIBUSB_FD_READY_GPHOTO_UNSUPPORTED" : "NATIVE_BACKEND_INIT_FAILED";
#else
    handle.libusb_init_code = -95;
    handle.libusb_wrap_code = -95;
    handle.backend_state = "NATIVE_BACKEND_UNSUPPORTED";
#endif
}

std::string device_info_json(const NativeHandle& handle) {
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("backend", backend_name()) << ","
        << source_state_json() << ","
        << "\"fdBridge\":\"dup\","
        << "\"fdState\":\"" << (handle.owned_fd >= 0 ? "open" : "closed") << "\","
        << "\"libusbInitialized\":" << (handle.libusb_initialized ? "true" : "false") << ","
        << "\"libusbWrapped\":" << (handle.libusb_wrapped ? "true" : "false") << ","
        << "\"libusbInitCode\":" << handle.libusb_init_code << ","
        << "\"libusbWrapCode\":" << handle.libusb_wrap_code << ","
        << "\"vendorId\":" << handle.vendor_id << ","
        << "\"productId\":" << handle.product_id;

#if CAMRAW_HAS_LIBUSB_SOURCE
    if (handle.usb_handle != nullptr) {
        libusb_device* device = libusb_get_device(handle.usb_handle);
        libusb_device_descriptor descriptor{};
        const int descriptor_code = libusb_get_device_descriptor(device, &descriptor);
        stream << ",\"libusbDescriptorCode\":" << descriptor_code;
        if (descriptor_code == 0) {
            stream
                << ",\"deviceClass\":" << static_cast<int>(descriptor.bDeviceClass)
                << ",\"deviceSubclass\":" << static_cast<int>(descriptor.bDeviceSubClass)
                << ",\"deviceProtocol\":" << static_cast<int>(descriptor.bDeviceProtocol)
                << ",\"usbConfigCount\":" << static_cast<int>(descriptor.bNumConfigurations);
        }
    }
#endif

    stream << "," << json_pair("backendState", handle.backend_state) << "}";
    return stream.str();
}

std::string capabilities_json(const NativeHandle& handle) {
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("backend", backend_name()) << ","
        << source_state_json() << ","
        << "\"fdBridge\":\"dup\","
        << "\"libusbInitialized\":" << (handle.libusb_initialized ? "true" : "false") << ","
        << "\"libusbWrapped\":" << (handle.libusb_wrapped ? "true" : "false") << ","
        << "\"libusbInitCode\":" << handle.libusb_init_code << ","
        << "\"libusbWrapCode\":" << handle.libusb_wrap_code << ","
        << "\"can_capture\":false,"
        << "\"can_tether\":false,"
        << "\"can_list_files\":false,"
        << "\"can_download_files\":false,"
        << "\"can_read_config\":false,"
        << "\"can_write_config\":false,"
        << "\"can_preview\":false,"
        << "\"vendorId\":" << handle.vendor_id << ","
        << "\"productId\":" << handle.product_id << ","
        << json_pair("backendState", handle.backend_state);
    stream << "}";
    return stream.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeBackendVersion(JNIEnv* env, jobject) {
    return to_jstring(
        env,
        std::string("camraw-gphoto-bridge/0.2 ")
            + "backend=" + backend_name()
            + " libusb=" + (CAMRAW_HAS_LIBUSB_SOURCE ? CAMRAW_LIBUSB_VERSION : "missing")
            + " libgphoto2=" + (CAMRAW_HAS_LIBGPHOTO2_SOURCE ? CAMRAW_LIBGPHOTO2_VERSION : "missing-source")
            + " gphotoBackend=unsupported"
    );
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeOpenFromFd(
    JNIEnv*,
    jobject,
    jint fd,
    jint vendor_id,
    jint product_id
) {
    if (fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "openFromFd rejected invalid fd=%d", fd);
        return 0;
    }

    NativeHandle native_handle{};
    native_handle.owned_fd = dup(fd);
    native_handle.vendor_id = vendor_id;
    native_handle.product_id = product_id;
    if (native_handle.owned_fd < 0) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "dup(fd=%d) failed", fd);
        return 0;
    }
    init_libusb_from_fd(native_handle);

    std::lock_guard<std::mutex> lock(g_mutex);
    const jlong handle = g_next_handle++;
    g_handles[handle] = native_handle;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "opened handle=%lld vendor=%04x product=%04x libusbInit=%d libusbWrap=%d",
        static_cast<long long>(handle),
        vendor_id,
        product_id,
        native_handle.libusb_init_code,
        native_handle.libusb_wrap_code
    );
    return handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeClose(JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto iterator = g_handles.find(handle);
    if (iterator == g_handles.end()) {
        return -2;
    }
    cleanup_handle(iterator->second);
    g_handles.erase(iterator);
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeGetDeviceInfoJson(JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("getDeviceInfo"));
    }
    return to_jstring(env, device_info_json(*native_handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeGetCapabilitiesJson(JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("getCapabilities"));
    }
    return to_jstring(env, capabilities_json(*native_handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeGetConfigJson(JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("getConfigJson"));
    }
    return to_jstring(env, unsupported_json(native_handle, "getConfigJson"));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeSetConfigValue(JNIEnv*, jobject, jlong, jstring, jstring) {
    return -95;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeCaptureJson(JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("capture"));
    }
    return to_jstring(env, unsupported_json(native_handle, "capture"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeWaitForEventJson(JNIEnv* env, jobject, jlong handle, jint) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("waitForEvent"));
    }
    return to_jstring(env, unsupported_json(native_handle, "waitForEvent"));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeListFilesJson(JNIEnv* env, jobject, jlong handle, jstring) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr) {
        return to_jstring(env, invalid_handle_json("listFiles"));
    }
    return to_jstring(env, unsupported_json(native_handle, "listFiles"));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeDownloadFile(JNIEnv*, jobject, jlong, jstring, jstring, jstring) {
    return -95;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeCancelOperation(JNIEnv*, jobject, jlong) {
    return 0;
}
