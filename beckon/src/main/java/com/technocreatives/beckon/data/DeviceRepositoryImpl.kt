package com.technocreatives.beckon.data

import android.content.Context
import androidx.core.content.edit
import arrow.core.Either
import arrow.core.identity
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

private const val KEY_DEVICES = "key.saved.devices"

internal class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

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

    private val moshi by lazy {
        Moshi.Builder().add(object {
            @ToJson
            fun toJson(uuid: UUID) = uuid.toString()

            @FromJson
            fun fromJson(s: String) = UUID.fromString(s)
        }).build()
    }

    private val adapter by lazy {
        val type = Types.newParameterizedType(List::class.java, SavedMetadata::class.java)
        moshi.adapter<List<SavedMetadata>>(type)
    }

    override suspend fun currentDevices(): List<SavedMetadata> {
        val json = sharedPreferences.getString(KEY_DEVICES, "")!!
        return if (json.isBlank()) {
            emptyList()
        } else {
            Either.catch {
                withContext(Dispatchers.IO) {
                    adapter.fromJson(json)!!
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
        val json = adapter.toJson(devices)
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
