package com.technocreatives.beckon.data

import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.Observable

internal interface DeviceRepository {
    fun saveDevices(devices: List<DeviceMetadata>): Observable<Unit>
    fun devices(): Observable<List<DeviceMetadata>>
}
