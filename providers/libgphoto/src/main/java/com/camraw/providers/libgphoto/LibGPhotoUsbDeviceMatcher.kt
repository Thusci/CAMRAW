package com.camraw.providers.libgphoto

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

data class UsbDescriptor(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceClasses: List<Int>,
)

object LibGPhotoUsbDeviceMatcher {
    fun fromDevice(device: UsbDevice): UsbDescriptor {
        return UsbDescriptor(
            vendorId = device.vendorId,
            productId = device.productId,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            interfaceClasses = (0 until device.interfaceCount).map { index ->
                device.getInterface(index).interfaceClass
            },
        )
    }

    fun isLikelyStillCamera(descriptor: UsbDescriptor): Boolean {
        return descriptor.deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
            descriptor.interfaceClasses.any { it == UsbConstants.USB_CLASS_STILL_IMAGE } ||
            descriptor.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
            descriptor.interfaceClasses.any { it == UsbConstants.USB_CLASS_VENDOR_SPEC }
    }

    fun displayName(descriptor: UsbDescriptor, productName: String?, manufacturerName: String?): String {
        val model = productName?.takeIf { it.isNotBlank() } ?: "USB Camera"
        val maker = manufacturerName?.takeIf { it.isNotBlank() }
        return if (maker == null || model.contains(maker, ignoreCase = true)) {
            model
        } else {
            "$maker $model"
        }
    }

    fun debugInfo(descriptor: UsbDescriptor): Map<String, String> {
        return mapOf(
            "vendorId" to descriptor.vendorId.toString(),
            "productId" to descriptor.productId.toString(),
            "deviceClass" to descriptor.deviceClass.toString(),
            "deviceSubclass" to descriptor.deviceSubclass.toString(),
            "deviceProtocol" to descriptor.deviceProtocol.toString(),
            "interfaceClasses" to descriptor.interfaceClasses.joinToString("|"),
            "likelyStillCamera" to isLikelyStillCamera(descriptor).toString(),
        )
    }
}
