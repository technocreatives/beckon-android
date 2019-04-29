package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import timber.log.Timber

class BeckonDevice<out Changes, out State>(
    context: Context,
    callbacks: BeckonManagerCallbacks,
    val device: BluetoothDevice,
    private val characteristics: List<Characteristics<Changes>>,
    private val reducer: Reducer<Changes, State>,
    defaultState: State
) : BleManager<BeckonManagerCallbacks>(context) {

    private val stateSubject = BehaviorSubject.createDefault(defaultState)

    private var bluetoothGatt: BluetoothGatt? = null

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
            bluetoothGatt?.let {
                characteristics.forEach { characteristics ->
                    isRequiredServiceSupported(it, characteristics)?.let { characteristics ->

                        characteristics.characteristics.forEach {
                            Timber.d("setNotification callback $it")

                            val callback = DataReceivedCallback { device, data ->
                                Timber.d("DataReceivedCallback $device $data")
                                val changes = it.mapper(data)
                                val newState = reducer(changes, stateSubject.value!!)
                                stateSubject.onNext(newState)
                            }
                            setNotificationCallback(it.characteristic).with(callback)
                            enableNotifications(it.characteristic).enqueue()
                            // setCharacteristic(it) }
                        }
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

            // bkCharacteristics = isRequiredServiceSupported(gatt, characteristics)
            return true
        }

        private fun isRequiredServiceSupported(
            gatt: BluetoothGatt,
            characteristics: Characteristics<Changes>
        ): BKCharacteristics<Changes>? {

            Timber.d("isRequiredServiceSupported $gatt $characteristics")

            val service = gatt.getService(characteristics.serviceUUID)

            if (service != null) {
                Timber.d("All characteristic $service ${service.characteristics.map { it.descriptors + " " + it.uuid }}")
                val chars = characteristics.characteristics.map {
                    BKCharacteristic(
                        service.getCharacteristic(it.characteristic),
                        it.mapper
                    )
                }
                return BKCharacteristics(service, chars)
            }
            return null
        }
    }

    init {
        mCallbacks = callbacks
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return gattCallback
    }

    fun doDisconnect() {
        this.disconnect().enqueue()
    }

    fun doConnect() {
        Timber.d("reconnect $device")
        connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .enqueue()
    }

    fun states(): Observable<out State> {
        return stateSubject.hide()
    }
}

data class BKCharacteristic<out Changes>(
    val characteristic: BluetoothGattCharacteristic?,
    val mapper: CharacteristicMapper<Changes>
)

data class BKCharacteristics<out Changes>(
    val service: BluetoothGattService,
    val characteristics: List<BKCharacteristic<Changes>>
)
