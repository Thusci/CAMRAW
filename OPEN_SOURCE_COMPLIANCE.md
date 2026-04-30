# Open Source Compliance

CAMRAW uses source-built LGPL libraries for multi-brand USB tethering.

## Strategy

- Build libtool/libltdl, libusb, and libgphoto2 from official source releases.
- Package LGPL libraries as dynamically linked `.so` files where required.
- Keep notices, source URLs, versions, and local build patches in the repository.
- Do not ship unknown third-party prebuilt binaries.
- Android USB access remains permission-gated: native code receives only an Android-authorized file descriptor.

## Current State

`providers/libgphoto` builds official source releases into dynamically linked Android `.so` libraries for `arm64-v8a`:

- `libcamraw_ltdl.so` from GNU libtool/libltdl 2.4.7.
- `libcamraw_libusb.so` from libusb 1.0.29.
- `libcamraw_gphoto2_port.so` and `libcamraw_gphoto2.so` from libgphoto2 2.5.33.
- `libcamraw_gphoto2_iolib_usb1.so` and `libcamraw_gphoto2_camlib_ptp2.so` as ltdl-loaded dynamic modules.

Native USB access remains Android-permission gated. JNI duplicates the `UsbDeviceConnection.fileDescriptor`, passes only the duplicate to libgphoto2/libusb, and closes only the duplicate on native cleanup. Operations that cannot initialize or execute return structured native errors and do not report fake success.

## Required Before Real libgphoto Release

- Document linker mode, patches, and reproducible build commands.
- Ensure release packaging continues to include the LGPL dynamic libraries and notices.
