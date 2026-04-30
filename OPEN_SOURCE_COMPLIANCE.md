# Open Source Compliance

CAMRAW will use source-built LGPL libraries for multi-brand USB tethering.

## Strategy

- Build libusb and libgphoto2 from official source releases.
- Package LGPL libraries as dynamically linked `.so` files where required.
- Keep notices, source URLs, versions, and local build patches in the repository.
- Do not ship unknown third-party prebuilt binaries.
- Android USB access remains permission-gated: native code receives only an Android-authorized file descriptor.

## Current State

`providers/libgphoto` now builds `libcamraw_gphoto_bridge.so` with libusb 1.0.29 source compiled into the bridge for `arm64-v8a`.

The official libgphoto2 2.5.33 source tree is unpacked under `third_party/libgphoto2-2.5.33`, but the full libgphoto2/camlibs Android backend is not initialized yet. Until then, native operations that require libgphoto2 return structured `NATIVE_BACKEND_UNSUPPORTED` / `NATIVE_BACKEND_INIT_FAILED` errors and do not report fake success.

## Required Before Real libgphoto Release

- Complete libgphoto2 port/camlibs Android build against the fd-backed libusb bridge.
- Document linker mode, patches, and reproducible build commands.
- Ensure final APK packaging includes required LGPL dynamic libraries and notices if libgphoto2 is linked dynamically.
