#include <jni.h>
#include <android/log.h>
#include <unistd.h>

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>

#if CAMRAW_HAS_LIBUSB_SOURCE
#include <libusb.h>
#endif

#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
extern "C" {
#include <gphoto2/gphoto2-abilities-list.h>
#include <gphoto2/gphoto2-camera.h>
#include <gphoto2/gphoto2-context.h>
#include <gphoto2/gphoto2-file.h>
#include <gphoto2/gphoto2-list.h>
#include <gphoto2/gphoto2-port.h>
#include <gphoto2/gphoto2-port-info-list.h>
#include <gphoto2/gphoto2-result.h>
}
#endif

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
    bool gphoto_initialized = false;
    int gphoto_init_code = 0;
    std::string backend_state = "NATIVE_BACKEND_UNSUPPORTED";
    std::string model;
    std::string port_path;
    std::string camera_library;
    std::string context_message;
#if CAMRAW_HAS_LIBUSB_SOURCE
    libusb_context* usb_context = nullptr;
    libusb_device_handle* usb_handle = nullptr;
#endif
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    GPContext* context = nullptr;
    Camera* camera = nullptr;
    CameraAbilities abilities{};
#endif
};

std::mutex g_mutex;
std::unordered_map<jlong, NativeHandle> g_handles;
jlong g_next_handle = 1;
std::string g_native_library_dir;
std::string g_last_open_error_json;

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

std::string json_bool(const char* key, bool value) {
    return std::string("\"") + key + "\":" + (value ? "true" : "false");
}

std::string parent_dir(const std::string& path) {
    const std::string::size_type slash = path.find_last_of('/');
    if (slash == std::string::npos || slash == 0) {
        return ".";
    }
    return path.substr(0, slash);
}

std::string bridge_library_dir() {
    Dl_info info{};
    if (dladdr(reinterpret_cast<void*>(&bridge_library_dir), &info) != 0 && info.dli_fname != nullptr) {
        return parent_dir(info.dli_fname);
    }
    return ".";
}

void configure_runtime_dir_locked(const std::string& native_library_dir) {
    if (!native_library_dir.empty()) {
        g_native_library_dir = native_library_dir;
    }
    if (g_native_library_dir.empty()) {
        g_native_library_dir = bridge_library_dir();
    }
    setenv("CAMLIBS", g_native_library_dir.c_str(), 1);
    setenv("IOLIBS", g_native_library_dir.c_str(), 1);
    setenv("CAMLIBS_PREFIX", "camraw_gphoto2_camlib", 1);
    setenv("IOLIBS_PREFIX", "camraw_gphoto2_iolib", 1);
}

NativeHandle* find_handle(jlong handle) {
    auto iterator = g_handles.find(handle);
    if (iterator == g_handles.end()) {
        return nullptr;
    }
    return &iterator->second;
}

std::string backend_name() {
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    return "libgphoto2";
#elif CAMRAW_HAS_LIBUSB_SOURCE
    return "libusb-fd-bridge";
#else
    return "stub";
#endif
}

std::string source_state_json() {
    return std::string("\"libtoolSource\":") + (CAMRAW_HAS_LIBTOOL_SOURCE ? "true" : "false") + ","
        + "\"libusbSource\":" + (CAMRAW_HAS_LIBUSB_SOURCE ? "true" : "false") + ","
        + "\"libgphoto2Source\":" + (CAMRAW_HAS_LIBGPHOTO2_SOURCE ? "true" : "false") + ","
        + "\"realGPhotoBackend\":" + (CAMRAW_HAS_REAL_GPHOTO_BACKEND ? "true" : "false") + ","
        + json_pair("libtoolVersion", CAMRAW_HAS_LIBTOOL_SOURCE ? CAMRAW_LIBTOOL_VERSION : "missing") + ","
        + json_pair("libusbVersion", CAMRAW_HAS_LIBUSB_SOURCE ? CAMRAW_LIBUSB_VERSION : "missing") + ","
        + json_pair("libgphoto2Version", CAMRAW_HAS_LIBGPHOTO2_SOURCE ? CAMRAW_LIBGPHOTO2_VERSION : "missing");
}

std::string result_string(int code) {
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    return gp_result_as_string(code);
#else
    return std::to_string(code);
#endif
}

std::string native_error_json(
    const std::string& operation,
    const std::string& error,
    const std::string& message,
    int native_code = 0,
    const NativeHandle* handle = nullptr
) {
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("error", error) << ","
        << json_pair("operation", operation) << ","
        << json_pair("backend", backend_name()) << ","
        << source_state_json() << ","
        << json_pair("message", message) << ","
        << "\"nativeCode\":" << native_code;
    if (native_code != 0) {
        stream << "," << json_pair("nativeCodeText", result_string(native_code));
    }
    if (!g_native_library_dir.empty()) {
        stream << "," << json_pair("nativeLibraryDir", g_native_library_dir);
    }
    if (handle != nullptr) {
        stream
            << ",\"fdBridge\":\"dup\""
            << ",\"fdState\":\"" << (handle->owned_fd >= 0 ? "open" : "closed") << "\""
            << ",\"libusbInitialized\":" << (handle->libusb_initialized ? "true" : "false")
            << ",\"libusbWrapped\":" << (handle->libusb_wrapped ? "true" : "false")
            << ",\"libusbInitCode\":" << handle->libusb_init_code
            << ",\"libusbWrapCode\":" << handle->libusb_wrap_code
            << ",\"gphotoInitialized\":" << (handle->gphoto_initialized ? "true" : "false")
            << ",\"gphotoInitCode\":" << handle->gphoto_init_code
            << ",\"vendorId\":" << handle->vendor_id
            << ",\"productId\":" << handle->product_id;
        if (!handle->context_message.empty()) {
            stream << "," << json_pair("contextMessage", handle->context_message);
        }
        if (!handle->model.empty()) {
            stream << "," << json_pair("model", handle->model);
        }
    }
    stream << "}";
    return stream.str();
}

std::string invalid_handle_json(const std::string& operation) {
    return native_error_json(operation, "NATIVE_HANDLE_INVALID", "native camera handle is no longer active");
}

std::string unsupported_json(const NativeHandle* handle, const std::string& operation) {
    return native_error_json(
        operation,
        "NATIVE_BACKEND_UNSUPPORTED",
        "libusb fd bridge is available, but the real libgphoto2 backend is not initialized",
        -95,
        handle
    );
}

void set_last_open_error_locked(const std::string& json) {
    g_last_open_error_json = json;
    __android_log_print(ANDROID_LOG_ERROR, kTag, "open failed: %s", json.c_str());
}

#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
void context_error_func(GPContext*, const char* text, void* data) {
    auto* handle = static_cast<NativeHandle*>(data);
    if (handle != nullptr && text != nullptr) {
        if (!handle->context_message.empty()) {
            handle->context_message += "\n";
        }
        handle->context_message += text;
    }
}

void context_status_func(GPContext*, const char* text, void* data) {
    auto* handle = static_cast<NativeHandle*>(data);
    if (handle != nullptr && text != nullptr && handle->context_message.empty()) {
        handle->context_message = text;
    }
}
#endif

void cleanup_handle(NativeHandle& handle) {
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    if (handle.camera != nullptr) {
        gp_camera_exit(handle.camera, handle.context);
        gp_camera_free(handle.camera);
        handle.camera = nullptr;
    }
    if (handle.context != nullptr) {
        gp_context_unref(handle.context);
        handle.context = nullptr;
    }
    gp_port_usb_set_sys_device(-1);
#endif
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

[[maybe_unused]] void init_libusb_from_fd(NativeHandle& handle) {
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

int init_gphoto_from_fd(NativeHandle& handle) {
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    configure_runtime_dir_locked("");

    handle.context = gp_context_new();
    if (handle.context == nullptr) {
        handle.gphoto_init_code = GP_ERROR_NO_MEMORY;
        handle.backend_state = "NATIVE_BACKEND_INIT_FAILED";
        return handle.gphoto_init_code;
    }
    gp_context_set_error_func(handle.context, context_error_func, &handle);
    gp_context_set_status_func(handle.context, context_status_func, &handle);
    gp_context_set_message_func(handle.context, context_status_func, &handle);

    int result = gp_port_usb_set_sys_device(handle.owned_fd);
    if (result < GP_OK) {
        handle.gphoto_init_code = result;
        handle.backend_state = "NATIVE_BACKEND_INIT_FAILED";
        return result;
    }

    result = gp_camera_new(&handle.camera);
    if (result < GP_OK) {
        handle.gphoto_init_code = result;
        handle.backend_state = "NATIVE_BACKEND_INIT_FAILED";
        return result;
    }

    result = gp_camera_init(handle.camera, handle.context);
    handle.gphoto_init_code = result;
    if (result < GP_OK) {
        handle.backend_state = "NATIVE_BACKEND_INIT_FAILED";
        return result;
    }

    result = gp_camera_get_abilities(handle.camera, &handle.abilities);
    if (result == GP_OK) {
        handle.model = handle.abilities.model;
        handle.camera_library = handle.abilities.library;
    }

    GPPortInfo port_info = nullptr;
    if (gp_camera_get_port_info(handle.camera, &port_info) == GP_OK && port_info != nullptr) {
        char* path = nullptr;
        if (gp_port_info_get_path(port_info, &path) == GP_OK && path != nullptr) {
            handle.port_path = path;
        }
    }

    handle.gphoto_initialized = true;
    handle.backend_state = "LIBGPHOTO2_READY";
    return GP_OK;
#else
    init_libusb_from_fd(handle);
    return handle.libusb_wrapped ? 0 : handle.libusb_wrap_code;
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
        << "\"gphotoInitialized\":" << (handle.gphoto_initialized ? "true" : "false") << ","
        << "\"gphotoInitCode\":" << handle.gphoto_init_code << ","
        << "\"vendorId\":" << handle.vendor_id << ","
        << "\"productId\":" << handle.product_id << ","
        << json_pair("backendState", handle.backend_state);
    if (!handle.model.empty()) {
        stream << "," << json_pair("model", handle.model);
    }
    if (!handle.port_path.empty()) {
        stream << "," << json_pair("portPath", handle.port_path);
    }
    if (!handle.camera_library.empty()) {
        stream << "," << json_pair("cameraLibrary", handle.camera_library);
    }
    if (!g_native_library_dir.empty()) {
        stream << "," << json_pair("nativeLibraryDir", g_native_library_dir);
    }
    stream << "}";
    return stream.str();
}

std::string capabilities_json(const NativeHandle& handle) {
    const bool ready = handle.gphoto_initialized;
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    const int operations = ready ? handle.abilities.operations : 0;
    const int file_operations = ready ? handle.abilities.file_operations : 0;
    const bool can_capture = (operations & GP_OPERATION_CAPTURE_IMAGE) != 0;
    const bool can_tether = (operations & GP_OPERATION_TRIGGER_CAPTURE) != 0;
    const bool can_read_config = (operations & GP_OPERATION_CONFIG) != 0;
    const bool can_preview = (operations & GP_OPERATION_CAPTURE_PREVIEW) != 0;
    const bool can_download = ready;
#else
    const int operations = 0;
    const int file_operations = 0;
    const bool can_capture = false;
    const bool can_tether = false;
    const bool can_read_config = false;
    const bool can_preview = false;
    const bool can_download = false;
#endif
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("backend", backend_name()) << ","
        << source_state_json() << ","
        << "\"fdBridge\":\"dup\","
        << "\"gphotoInitialized\":" << (ready ? "true" : "false") << ","
        << "\"gphotoInitCode\":" << handle.gphoto_init_code << ","
        << json_bool("can_capture", can_capture) << ","
        << json_bool("can_tether", can_tether) << ","
        << json_bool("can_list_files", ready) << ","
        << json_bool("can_download_files", can_download) << ","
        << json_bool("can_read_config", can_read_config) << ","
        << "\"can_write_config\":false,"
        << json_bool("can_preview", can_preview) << ","
        << "\"operations\":" << operations << ","
        << "\"fileOperations\":" << file_operations << ","
        << "\"vendorId\":" << handle.vendor_id << ","
        << "\"productId\":" << handle.product_id << ","
        << json_pair("backendState", handle.backend_state);
    if (!handle.model.empty()) {
        stream << "," << json_pair("model", handle.model);
    }
    stream << "}";
    return stream.str();
}

bool require_ready(JNIEnv* env, NativeHandle* handle, const std::string& operation, jstring* result) {
    if (handle == nullptr) {
        *result = to_jstring(env, invalid_handle_json(operation));
        return false;
    }
    if (!handle->gphoto_initialized) {
        *result = to_jstring(env, unsupported_json(handle, operation));
        return false;
    }
    return true;
}

std::string list_files_json(NativeHandle& handle, const std::string& folder) {
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    CameraList* list = nullptr;
    int result = gp_list_new(&list);
    if (result < GP_OK) {
        return native_error_json("listFiles", "NATIVE_BACKEND_INIT_FAILED", "could not allocate libgphoto2 list", result, &handle);
    }
    result = gp_camera_folder_list_files(handle.camera, folder.c_str(), list, handle.context);
    if (result < GP_OK) {
        gp_list_free(list);
        return native_error_json("listFiles", "GPHOTO_OPERATION_FAILED", "libgphoto2 could not list camera files", result, &handle);
    }
    const int count = gp_list_count(list);
    std::ostringstream stream;
    stream << "{" << json_pair("backend", backend_name()) << "," << json_pair("folder", folder) << ",\"files\":[";
    for (int index = 0; index < count; ++index) {
        const char* name = nullptr;
        gp_list_get_name(list, index, &name);
        if (index > 0) {
            stream << ",";
        }
        stream << "\"" << escape_json(name != nullptr ? name : "") << "\"";
    }
    stream << "]}";
    gp_list_free(list);
    return stream.str();
#else
    return unsupported_json(&handle, "listFiles");
#endif
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeConfigureRuntime(
    JNIEnv* env,
    jobject,
    jstring native_library_dir
) {
    const char* chars = native_library_dir != nullptr ? env->GetStringUTFChars(native_library_dir, nullptr) : nullptr;
    std::lock_guard<std::mutex> lock(g_mutex);
    configure_runtime_dir_locked(chars != nullptr ? chars : "");
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(native_library_dir, chars);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeBackendVersion(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    configure_runtime_dir_locked("");
    return to_jstring(
        env,
        std::string("camraw-gphoto-bridge/0.3 ")
            + "backend=" + backend_name()
            + " libtool=" + (CAMRAW_HAS_LIBTOOL_SOURCE ? CAMRAW_LIBTOOL_VERSION : "missing")
            + " libusb=" + (CAMRAW_HAS_LIBUSB_SOURCE ? CAMRAW_LIBUSB_VERSION : "missing")
            + " libgphoto2=" + (CAMRAW_HAS_LIBGPHOTO2_SOURCE ? CAMRAW_LIBGPHOTO2_VERSION : "missing-source")
            + " nativeLibraryDir=" + g_native_library_dir
    );
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeLastOpenErrorJson(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return to_jstring(env, g_last_open_error_json.empty() ? "{}" : g_last_open_error_json);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeOpenFromFd(
    JNIEnv*,
    jobject,
    jint fd,
    jint vendor_id,
    jint product_id
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    configure_runtime_dir_locked("");

    if (fd < 0) {
        set_last_open_error_locked(native_error_json("openFromFd", "NATIVE_BACKEND_INIT_FAILED", "invalid Android USB file descriptor", -1));
        return 0;
    }

    NativeHandle native_handle{};
    native_handle.owned_fd = dup(fd);
    native_handle.vendor_id = vendor_id;
    native_handle.product_id = product_id;
    if (native_handle.owned_fd < 0) {
        set_last_open_error_locked(native_error_json("openFromFd", "NATIVE_BACKEND_INIT_FAILED", std::string("dup failed: ") + strerror(errno), -1));
        return 0;
    }

    const int init_result = init_gphoto_from_fd(native_handle);
    if (CAMRAW_HAS_REAL_GPHOTO_BACKEND && init_result < 0) {
        const std::string message = native_handle.context_message.empty()
            ? "libgphoto2 could not initialize a camera from the authorized Android USB fd"
            : native_handle.context_message;
        set_last_open_error_locked(native_error_json("openFromFd", "NATIVE_BACKEND_INIT_FAILED", message, init_result, &native_handle));
        cleanup_handle(native_handle);
        return 0;
    }
    if (!CAMRAW_HAS_REAL_GPHOTO_BACKEND && native_handle.backend_state == "NATIVE_BACKEND_INIT_FAILED") {
        set_last_open_error_locked(native_error_json("openFromFd", "NATIVE_BACKEND_INIT_FAILED", "libusb fd bridge initialization failed", init_result, &native_handle));
        cleanup_handle(native_handle);
        return 0;
    }

    const jlong handle = g_next_handle++;
    g_handles[handle] = native_handle;
    g_last_open_error_json.clear();
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "opened handle=%lld vendor=%04x product=%04x backend=%s gphotoInit=%d",
        static_cast<long long>(handle),
        vendor_id,
        product_id,
        backend_name().c_str(),
        native_handle.gphoto_init_code
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
    jstring error = nullptr;
    if (!require_ready(env, native_handle, "getConfigJson", &error)) {
        return error;
    }
    return to_jstring(env, native_error_json("getConfigJson", "NATIVE_BACKEND_UNSUPPORTED", "config widget JSON serialization is not wired yet", -95, native_handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeSetConfigValue(JNIEnv*, jobject, jlong, jstring, jstring) {
    return -95;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeCaptureJson(JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    jstring error = nullptr;
    if (!require_ready(env, native_handle, "capture", &error)) {
        return error;
    }
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    CameraFilePath path{};
    const int result = gp_camera_capture(native_handle->camera, GP_CAPTURE_IMAGE, &path, native_handle->context);
    if (result < GP_OK) {
        return to_jstring(env, native_error_json("capture", "GPHOTO_OPERATION_FAILED", "libgphoto2 capture failed", result, native_handle));
    }
    std::ostringstream stream;
    stream
        << "{"
        << json_pair("backend", backend_name()) << ","
        << json_pair("folder", path.folder) << ","
        << json_pair("filename", path.name)
        << "}";
    return to_jstring(env, stream.str());
#else
    return to_jstring(env, unsupported_json(native_handle, "capture"));
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeWaitForEventJson(JNIEnv* env, jobject, jlong handle, jint timeout_ms) {
    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    jstring error = nullptr;
    if (!require_ready(env, native_handle, "waitForEvent", &error)) {
        return error;
    }
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    CameraEventType event_type = GP_EVENT_UNKNOWN;
    void* event_data = nullptr;
    const int result = gp_camera_wait_for_event(native_handle->camera, timeout_ms, &event_type, &event_data, native_handle->context);
    if (result < GP_OK) {
        return to_jstring(env, native_error_json("waitForEvent", "GPHOTO_OPERATION_FAILED", "libgphoto2 wait_for_event failed", result, native_handle));
    }
    const char* type = "Unknown";
    std::string folder;
    std::string filename;
    switch (event_type) {
        case GP_EVENT_TIMEOUT: type = "Timeout"; break;
        case GP_EVENT_FILE_ADDED: {
            type = "ObjectAdded";
            auto* path = static_cast<CameraFilePath*>(event_data);
            if (path != nullptr) {
                folder = path->folder;
                filename = path->name;
            }
            break;
        }
        case GP_EVENT_FOLDER_ADDED: type = "FolderAdded"; break;
        case GP_EVENT_CAPTURE_COMPLETE: type = "CaptureComplete"; break;
        case GP_EVENT_FILE_CHANGED: type = "FileChanged"; break;
        case GP_EVENT_UNKNOWN:
        default: type = "Unknown"; break;
    }
    free(event_data);
    std::ostringstream stream;
    stream << "{" << json_pair("backend", backend_name()) << "," << json_pair("event", type);
    if (!folder.empty()) {
        stream << "," << json_pair("folder", folder);
    }
    if (!filename.empty()) {
        stream << "," << json_pair("filename", filename);
    }
    stream << "}";
    return to_jstring(env, stream.str());
#else
    return to_jstring(env, unsupported_json(native_handle, "waitForEvent"));
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeListFilesJson(JNIEnv* env, jobject, jlong handle, jstring folder) {
    const char* folder_chars = folder != nullptr ? env->GetStringUTFChars(folder, nullptr) : nullptr;
    const std::string folder_value = folder_chars != nullptr && strlen(folder_chars) > 0 ? folder_chars : "/";
    if (folder_chars != nullptr) {
        env->ReleaseStringUTFChars(folder, folder_chars);
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    jstring error = nullptr;
    if (!require_ready(env, native_handle, "listFiles", &error)) {
        return error;
    }
    return to_jstring(env, list_files_json(*native_handle, folder_value));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeDownloadFile(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring folder,
    jstring filename,
    jstring target_path
) {
    const char* folder_chars = folder != nullptr ? env->GetStringUTFChars(folder, nullptr) : nullptr;
    const char* filename_chars = filename != nullptr ? env->GetStringUTFChars(filename, nullptr) : nullptr;
    const char* target_chars = target_path != nullptr ? env->GetStringUTFChars(target_path, nullptr) : nullptr;
    const std::string folder_value = folder_chars != nullptr ? folder_chars : "/";
    const std::string filename_value = filename_chars != nullptr ? filename_chars : "";
    const std::string target_value = target_chars != nullptr ? target_chars : "";
    if (folder_chars != nullptr) env->ReleaseStringUTFChars(folder, folder_chars);
    if (filename_chars != nullptr) env->ReleaseStringUTFChars(filename, filename_chars);
    if (target_chars != nullptr) env->ReleaseStringUTFChars(target_path, target_chars);

    std::lock_guard<std::mutex> lock(g_mutex);
    NativeHandle* native_handle = find_handle(handle);
    if (native_handle == nullptr || !native_handle->gphoto_initialized) {
        return -95;
    }
#if CAMRAW_HAS_REAL_GPHOTO_BACKEND
    CameraFile* file = nullptr;
    int result = gp_file_new(&file);
    if (result < GP_OK) {
        return result;
    }
    result = gp_camera_file_get(native_handle->camera, folder_value.c_str(), filename_value.c_str(), GP_FILE_TYPE_NORMAL, file, native_handle->context);
    if (result == GP_OK) {
        result = gp_file_save(file, target_value.c_str());
    }
    gp_file_free(file);
    return result;
#else
    return -95;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_camraw_providers_libgphoto_LibGPhotoNative_nativeCancelOperation(JNIEnv*, jobject, jlong) {
    return 0;
}
