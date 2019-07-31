package com.technocreatives.beckon.data

import arrow.core.Option
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.WritableDeviceMetadata
import io.reactivex.Observable
import io.reactivex.Single

internal interface DeviceRepository {

    fun currentDevices(): List<WritableDeviceMetadata>

    fun addDevice(metadata: WritableDeviceMetadata): Single<List<WritableDeviceMetadata>>

    fun removeDevice(macAddress: MacAddress): Single<List<WritableDeviceMetadata>>

    fun findDevice(macAddress: MacAddress): Single<Option<WritableDeviceMetadata>>

    fun saveDevices(devices: List<WritableDeviceMetadata>): Single<List<WritableDeviceMetadata>>

    fun devices(): Observable<List<WritableDeviceMetadata>>
}
