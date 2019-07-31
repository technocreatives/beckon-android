package com.technocreatives.beckon.data

import android.content.Context
import androidx.core.content.edit
import arrow.core.Option
import arrow.core.toOption
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.WritableDeviceMetadata
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import java.util.UUID

private const val KEY_DEVICES = "key.saved.devices"

internal class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

    private val devicesSubject by lazy {
        BehaviorSubject.createDefault(currentDevices())
    }

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
        val type = Types.newParameterizedType(List::class.java, WritableDeviceMetadata::class.java)
        moshi.adapter<List<WritableDeviceMetadata>>(type)
    }

    override fun currentDevices(): List<WritableDeviceMetadata> {
        val json = sharedPreferences.getString(KEY_DEVICES, "")!!
        return if (json.isBlank()) {
            emptyList()
        } else {
            adapter.fromJson(json)!!
        }
    }

    override fun addDevice(metadata: WritableDeviceMetadata): Single<List<WritableDeviceMetadata>> {
        val currentDevices = currentDevices()
        return if (!currentDevices.any { it.macAddress == metadata.macAddress }) {
            val devices = currentDevices + metadata
            saveDevices(devices)
        } else {
            Single.just(currentDevices)
        }
    }

    override fun removeDevice(macAddress: String): Single<List<WritableDeviceMetadata>> {
        val currentDevices = currentDevices()
        val device = currentDevices.find { it.macAddress == macAddress }
        return if (device != null) {
            val devices = currentDevices - device
            saveDevices(devices)
        } else {
            Single.just(currentDevices)
        }
    }

    override fun findDevice(macAddress: MacAddress): Single<Option<WritableDeviceMetadata>> {
        val currentDevices = currentDevices()
        return Single.just(currentDevices.find { it.macAddress == macAddress }.toOption())
    }

    override fun saveDevices(devices: List<WritableDeviceMetadata>): Single<List<WritableDeviceMetadata>> {
        return Single.create { emitter ->
            try {
                val json = adapter.toJson(devices)
                sharedPreferences.edit(commit = true) {
                    this.putString(KEY_DEVICES, json)
                }
                emitter.onSuccess(devices)
                devicesSubject.onNext(devices)
            } catch (ex: Throwable) {
                emitter.onError(ex)
            }
        }
    }

    override fun devices(): Observable<List<WritableDeviceMetadata>> {
        return devicesSubject.hide()
    }
}
