# CAMRAW Native Source Intake

The libgphoto tether stack is designed to build official source releases, not unknown prebuilt binaries.

Source inputs:

- libusb 1.0.29: https://github.com/libusb/libusb/releases/tag/v1.0.29
- libgphoto2 2.5.33: https://github.com/gphoto/libgphoto2/releases/tag/v2.5.33
- GNU libtool/libltdl 2.4.7: https://ftp.gnu.org/gnu/libtool/libtool-2.4.7.tar.xz

Verified local archives:

- `third_party_src/libusb-1.0.29.tar.bz2`
  - SHA-256: `5977fc950f8d1395ccea9bd48c06b3f808fd3c2c961b44b0c2e6e29fc3a70a85`
- `third_party_src/libgphoto2-2.5.33.tar.xz`
  - SHA-256: `28825f767a85544cb58f6e15028f8e53a5bb37a62148b3f1708b524781c3bef2`
- `third_party_src/libtool-2.4.7.tar.xz`
  - SHA-256: `4f7f217f057ce655ff22559ad221a0fd8ef84ad1fc5fcb6990cecc333aa1635d`

Current repository state:

- `providers/libgphoto` includes the Android USB fd bridge, JNI API, and source-built CMake targets for `arm64-v8a`.
- `third_party/libtool-2.4.7` is unpacked and `libltdl` is built as `libcamraw_ltdl.so`.
- `third_party/libusb-1.0.29` is unpacked and built as `libcamraw_libusb.so`.
- `third_party/libgphoto2-2.5.33` is unpacked and built as `libcamraw_gphoto2.so` plus `libcamraw_gphoto2_port.so`.
- libgphoto2's upstream dynamic loading shape is preserved: `camlibs/ptp2` builds as `libcamraw_gphoto2_camlib_ptp2.so`, and `iolibs/usb1` builds as `libcamraw_gphoto2_iolib_usb1.so`.
- Native code never scans USB directly before Android permission; it accepts only `UsbDeviceConnection.fileDescriptor`, duplicates that fd in JNI, and passes the duplicate to libgphoto2_port's `gp_port_usb_set_sys_device`.
- If libltdl/libusb/libgphoto2 initialization fails, operations return structured native errors such as `NATIVE_BACKEND_INIT_FAILED`; fallback code does not fake list/capture/download success.

Source drop convention:

```text
third_party/
├── libtool-2.4.7/
├── libusb-1.0.29/
└── libgphoto2-2.5.33/
```
