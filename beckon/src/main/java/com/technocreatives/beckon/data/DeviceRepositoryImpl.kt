package com.technocreatives.beckon.data

import android.content.Context
import androidx.core.content.edit
import arrow.core.Either
import arrow.core.identity
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val KEY_DEVICES = "key.saved.devices"

internal class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    private fun List<SavedMetadata>.toJson(): String =
        json.encodeToString(this)

    private fun String.toData(): List<SavedMetadata> =
        json.decodeFromString(this)

    private val devicesSubject by lazy {
        MutableSharedFlow<List<SavedMetadata>>(1)
    }

    private val devicesFlow = devicesSubject.asSharedFlow()

    private val sharedPreferences by lazy {
        context.getSharedPreferences(
            "com.technocreatives.beckon",
            Context.MODE_PRIVATE
        )
    }

    override suspend fun currentDevices(): List<SavedMetadata> {
        val json = sharedPreferences.getString(KEY_DEVICES, "")!!
        return if (json.isBlank()) {
            emptyList()
        } else {
            Either.catch {
                withContext(Dispatchers.IO) {
                    json.toData()
                }
            }.fold({ emptyList() }, ::identity)
        }
    }

    override suspend fun addDevice(metadata: SavedMetadata): List<SavedMetadata> {
        val currentDevices = currentDevices()
        return if (!currentDevices.any { it.macAddress == metadata.macAddress }) {
            val devices = currentDevices + metadata
            saveDevices(devices)
        } else {
            currentDevices
        }
    }

    override suspend fun removeDevice(macAddress: String): List<SavedMetadata> {
        val currentDevices = currentDevices()
        val device = currentDevices.find { it.macAddress == macAddress }
        return if (device != null) {
            val devices = currentDevices - device
            saveDevices(devices)
        } else {
            currentDevices
        }
    }

    override suspend fun findDevice(macAddress: MacAddress): SavedMetadata? {
        val currentDevices = currentDevices()
        return currentDevices.find { it.macAddress == macAddress }
    }

    override suspend fun saveDevices(devices: List<SavedMetadata>): List<SavedMetadata> {
        val json = devices.toJson()
        sharedPreferences.edit(commit = true) {
            this.putString(KEY_DEVICES, json)
        }
        devicesSubject.emit(devices)
        return devices
    }

    override fun devices(): Flow<List<SavedMetadata>> {
        return devicesFlow
    }
}
