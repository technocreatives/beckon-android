package com.technocreatives.beckon.data

import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.Observable

internal interface DeviceRepository {

    fun currentDevices(): List<DeviceMetadata>

    // single
    fun addDevice(metadata: DeviceMetadata): Observable<List<DeviceMetadata>>

    // single
    fun removeDevice(macAddress: String): Observable<List<DeviceMetadata>>

    // single
    fun saveDevices(devices: List<DeviceMetadata>): Observable<List<DeviceMetadata>>

    fun devices(): Observable<List<DeviceMetadata>>
}
