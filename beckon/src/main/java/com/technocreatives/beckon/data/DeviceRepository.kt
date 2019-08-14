package com.technocreatives.beckon.data

import arrow.core.Option
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import io.reactivex.Observable
import io.reactivex.Single

internal interface DeviceRepository {

    fun currentDevices(): List<SavedMetadata>

    fun addDevice(metadata: SavedMetadata): Single<List<SavedMetadata>>

    fun removeDevice(macAddress: MacAddress): Single<List<SavedMetadata>>

    fun findDevice(macAddress: MacAddress): Single<Option<SavedMetadata>>

    fun saveDevices(devices: List<SavedMetadata>): Single<List<SavedMetadata>>

    fun devices(): Observable<List<SavedMetadata>>
}
