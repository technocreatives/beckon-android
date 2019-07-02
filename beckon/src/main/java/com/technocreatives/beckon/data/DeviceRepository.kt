package com.technocreatives.beckon.data

import com.technocreatives.beckon.WritableDeviceMetadata
import io.reactivex.Observable
import io.reactivex.Single

internal interface DeviceRepository {

    fun currentDevices(): List<WritableDeviceMetadata>

    // single
    fun addDevice(metadata: WritableDeviceMetadata): Single<List<WritableDeviceMetadata>>

    // single
    fun removeDevice(macAddress: String): Single<List<WritableDeviceMetadata>>

    // single
    fun saveDevices(devices: List<WritableDeviceMetadata>): Single<List<WritableDeviceMetadata>>

    fun devices(): Observable<List<WritableDeviceMetadata>>
}
