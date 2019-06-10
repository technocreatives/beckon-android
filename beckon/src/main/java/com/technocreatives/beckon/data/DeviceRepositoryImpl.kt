package com.technocreatives.beckon.data

import android.content.Context
import androidx.core.content.edit
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.technocreatives.beckon.DeviceMetadata
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import java.util.UUID

private const val KEY_DEVICES = "key.saved.devices"

internal class DeviceRepositoryImpl(private val context: Context) : DeviceRepository {

    private val devicesSubject by lazy {
        BehaviorSubject.createDefault(currentDevices())
    }

    private val sharedPreferences by lazy {
        context.getSharedPreferences(
                "com.technocreative.beckon",
                Context.MODE_PRIVATE
        )
    }

    private val moshi by lazy {
        Moshi.Builder().add(object {
            @ToJson
            fun toJson(uuid: UUID) = uuid.toString()

            @FromJson
            fun fromJson(s: String) = UUID.fromString(s)
        })
                .build()
    }

    private val adapter by lazy {
        val type = Types.newParameterizedType(List::class.java, DeviceMetadata::class.java)
        moshi.adapter<List<DeviceMetadata>>(type)
    }

    override fun currentDevices(): List<DeviceMetadata> {
        val json = sharedPreferences.getString(KEY_DEVICES, "")!!
        return if (json.isBlank()) {
            emptyList()
        } else {
            adapter.fromJson(json)!!
        }
    }

    override fun addDevice(metadata: DeviceMetadata): Observable<List<DeviceMetadata>> {
        val currentDevices = currentDevices()
        return if (!currentDevices.any { it.macAddress == metadata.macAddress }) {
            val devices = currentDevices + metadata
            saveDevices(devices)
        } else {
            Observable.just(currentDevices)
        }
    }

    override fun removeDevice(macAddress: String): Observable<List<DeviceMetadata>> {
        val currentDevices = currentDevices()
        val device = currentDevices.find { it.macAddress == macAddress }
        return if (device != null) {
            val devices = currentDevices - device
            saveDevices(devices)
        } else {
            Observable.just(currentDevices)
        }
    }

    override fun saveDevices(devices: List<DeviceMetadata>): Observable<List<DeviceMetadata>> {
        return Observable.create { emitter ->
            try {
                val json = adapter.toJson(devices)
                sharedPreferences.edit(commit = true) {
                    this.putString(KEY_DEVICES, json)
                }
                emitter.onNext(devices)
                devicesSubject.onNext(devices)
                emitter.onComplete()
            } catch (ex: Throwable) {
                emitter.onError(ex)
            }
        }
    }

    override fun devices(): Observable<List<DeviceMetadata>> {
        return devicesSubject.hide()
    }
}
