package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothGatt
import android.content.Context
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicFailureException
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Type
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonBleManager(
    context: Context,
    private val characteristics: List<Characteristic>
) : BleManager<BeckonManagerCallbacks>(context) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val connectionSubject: BehaviorSubject<ConnectionState> = BehaviorSubject.createDefault(ConnectionState.NotStarted)
    private val changeSubject = PublishSubject.create<Pair<Characteristic, Data>>()
    private val discoveredDevice = PublishSubject.create<List<CharacteristicResult>>()

    init {
        mCallbacks = BeckonManagerCallbacks(connectionSubject)
    }

    fun connect(request: ConnectRequest): Observable<List<CharacteristicResult>> {
        request.enqueue()
        return discoveredDevice.hide()
    }

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
            Timber.d("initialize")
            bluetoothGatt?.let { gatt ->
                val result = characteristics.map { findBluetoothGattCharacteristic(gatt, it) }
                        .map { setupCallback(it) }
                discoveredDevice.onNext(result)
            }
        }

        private fun setupCallback(result: CharacteristicResult): CharacteristicResult {
            if (result is CharacteristicResult.Success) {
                if (result.characteristic.types.contains(Type.NOTIFY)) {
                    setupNotificationCallback(result)
                }
            }
            return result
        }

        private fun setupNotificationCallback(success: CharacteristicResult.Success): CharacteristicResult {
            Timber.d("setNotification callback $success")
            val callback = DataReceivedCallback { device, data ->
                Timber.d("DataReceivedCallback $device $data")
                changeSubject.onNext(success.characteristic to data)
            }

            setNotificationCallback(success.gatt).with(callback)
            enableNotifications(success.gatt).enqueue()
            return success
        }

        override fun onDeviceDisconnected() {
            Timber.d("onDeviceDisconnected gattCallback")
        }

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            Timber.d("isRequiredServiceSupported $gatt")
            val services = gatt.services.map { it.uuid }
            Timber.d("All services $services")
            bluetoothGatt = gatt

            return true
        }

        private fun findBluetoothGattCharacteristic(
            gatt: BluetoothGatt,
            characteristic: Characteristic
        ): CharacteristicResult {

            Timber.d("isRequiredServiceSupported $gatt $characteristic")

            val service = gatt.getService(characteristic.service)

            return if (service != null) {
                Timber.d("All characteristic $service ${service.characteristics.map { it.descriptors + " " + it.uuid }}")
                val bluetoothGattCharacteristic = service.getCharacteristic(characteristic.uuid)
                if (bluetoothGattCharacteristic == null) {
                    CharacteristicResult.Failed(characteristic, CharacteristicFailureException("BluetoothGatt not found!"))
                } else {
                    CharacteristicResult.Success(characteristic, bluetoothGattCharacteristic)
                }
            } else {
                CharacteristicResult.Failed(characteristic, CharacteristicFailureException("Service ${characteristic.service} not found!"))
            }
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return gattCallback
    }

    override fun log(priority: Int, message: String) {
        Timber.d(message)
    }

    fun connectionState(): Observable<ConnectionState> {
        return connectionSubject.hide()
    }

    fun changes(): Observable<Pair<Characteristic, Data>> {
        return changeSubject.hide()
    }

    fun currentState(): ConnectionState {
        return connectionSubject.value!!
    }
}