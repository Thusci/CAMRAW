# Third Party Licenses

CAMRAW's USB tether implementation uses LGPL components:

- libusb 1.0.29
- libgphoto2 2.5.33
- GNU libtool/libltdl 2.4.7

Upstream license files copied here:

- `libusb-1.0.29.COPYING`
- `libgphoto2-2.5.33.COPYING`
- `libgphoto2_port-2.5.33.COPYING.LIB`
- `libltdl-2.4.7.COPYING.LIB`

The current native bridge builds these libraries from source as Android arm64-v8a `.so` files. libgphoto2's `ptp2` camlib and `usb1` iolib remain dynamically loaded through libltdl.
