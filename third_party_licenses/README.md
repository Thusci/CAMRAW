# Third Party Licenses

CAMRAW's USB tether implementation uses LGPL components:

- libusb 1.0.29
- libgphoto2 2.5.33

Upstream license files copied here:

- `libusb-1.0.29.COPYING`
- `libgphoto2-2.5.33.COPYING`
- `libgphoto2_port-2.5.33.COPYING.LIB`

The current native bridge compiles libusb from source. The libgphoto2 source tree is unpacked but still returns structured unsupported errors until its Android backend/camlibs are wired in.
