# CAMRAW Native Source Intake

The libgphoto tether stack is designed to build official source releases, not unknown prebuilt binaries.

Source inputs:

- libusb 1.0.29: https://github.com/libusb/libusb/releases/tag/v1.0.29
- libgphoto2 2.5.33: https://github.com/gphoto/libgphoto2/releases/tag/v2.5.33

Verified local archives:

- `third_party_src/libusb-1.0.29.tar.bz2`
  - SHA-256: `5977fc950f8d1395ccea9bd48c06b3f808fd3c2c961b44b0c2e6e29fc3a70a85`
- `third_party_src/libgphoto2-2.5.33.tar.xz`
  - SHA-256: `28825f767a85544cb58f6e15028f8e53a5bb37a62148b3f1708b524781c3bef2`

Current repository state:

- `providers/libgphoto` includes the Android USB fd bridge, JNI API, and `libcamraw_gphoto_bridge.so` CMake target.
- `third_party/libusb-1.0.29` is unpacked and built into `libcamraw_gphoto_bridge.so` as a static CMake target for `arm64-v8a`.
- `third_party/libgphoto2-2.5.33` is unpacked and version-tracked, but the full libgphoto2/camlibs backend is not initialized yet.
- Until the libgphoto2 backend is completed, capture/list/download return structured `NATIVE_BACKEND_UNSUPPORTED` or `NATIVE_BACKEND_INIT_FAILED` errors.
- Native code never scans USB directly; it only accepts `UsbDeviceConnection.fileDescriptor` after Android grants USB permission.

Source drop convention:

```text
third_party/
├── libusb-1.0.29/
└── libgphoto2-2.5.33/
```

Next native step: wire libgphoto2 port/camlibs to the already working libusb fd bridge while keeping Android fd ownership via `dup(fd)`.
