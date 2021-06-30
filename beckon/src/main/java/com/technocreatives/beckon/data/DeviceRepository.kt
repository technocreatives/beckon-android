package com.technocreatives.beckon.data

import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import kotlinx.coroutines.flow.Flow

// return Either
interface DeviceRepository {
    suspend fun currentDevices(): List<SavedMetadata>

    suspend fun addDevice(metadata: SavedMetadata): List<SavedMetadata>

    suspend fun removeDevice(macAddress: MacAddress): List<SavedMetadata>

    suspend fun findDevice(macAddress: MacAddress): SavedMetadata?

    suspend fun saveDevices(devices: List<SavedMetadata>): List<SavedMetadata>

    fun devices(): Flow<List<SavedMetadata>>
}
