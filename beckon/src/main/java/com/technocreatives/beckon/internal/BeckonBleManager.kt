package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.ConnectionState
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import timber.log.Timber

internal class BeckonBleManager(
    context: Context,
    private val characteristics: List<Characteristic>
) : BleManager<BeckonManagerCallbacks>(context) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val connectionSubject: BehaviorSubject<ConnectionState> = BehaviorSubject.createDefault(ConnectionState.NotStarted)
    private val changeSubject = PublishSubject.create<Change>()

    init {
        mCallbacks = BeckonManagerCallbacks(connectionSubject)
    }

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
            Timber.d("initialize")
            bluetoothGatt?.let {
                characteristics.forEach { characteristic ->
                    isRequiredServiceSupported(it, characteristic)?.let { pair ->

                        Timber.d("setNotification callback $it")

                        val callback = DataReceivedCallback { device, data ->
                            Timber.d("DataReceivedCallback $device $data")
                            changeSubject.onNext(Change(pair.first.uuid, data))
                        }

                        setNotificationCallback(pair.second).with(callback)
                        enableNotifications(pair.second).enqueue()
                    }
                }
            }
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

        private fun isRequiredServiceSupported(
            gatt: BluetoothGatt,
            characteristic: Characteristic
        ): Pair<Characteristic, BluetoothGattCharacteristic>? {

            Timber.d("isRequiredServiceSupported $gatt $characteristic")

            val service = gatt.getService(characteristic.service)

            if (service != null) {
                Timber.d("All characteristic $service ${service.characteristics.map { it.descriptors + " " + it.uuid }}")
                return characteristic to service.getCharacteristic(characteristic.uuid)
            }

            return null
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

    fun changes(): Observable<Change> {
        return changeSubject.hide()
    }

    fun currentState(): ConnectionState {
        return connectionSubject.value!!
    }
}