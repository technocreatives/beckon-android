package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import timber.log.Timber

internal class BeckonDeviceImpl(
    context: Context,
    private val device: BluetoothDevice,
    private val characteristics: List<Characteristic>
) : BleManager<BeckonManagerCallbacks>(context), BeckonDevice {

    private val changeSubject = PublishSubject.create<Change>()
    private val connectionSubject: BehaviorSubject<ConnectionState> = BehaviorSubject.createDefault(ConnectionState.NotStarted)

    private var bluetoothGatt: BluetoothGatt? = null

    init {
        mCallbacks = BeckonManagerCallbacks(connectionSubject)
    }

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
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

            // bkCharacteristics = isRequiredServiceSupported(gatt, characteristics)
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

    override fun macAddress(): String {
        return device.address
    }

    override fun name(): String {
        return device.name
    }

    override fun connectionState(): Observable<ConnectionState> {
        return connectionSubject.hide()
    }

    override fun changes(): Observable<Change> {
        return changeSubject.hide()
    }

    override fun currentStates(): List<Change> {
        throw UnsupportedOperationException("not implemented")
    }

    override fun doConnect(autoConnect: Boolean): Observable<ConnectionState> {
        Timber.d("reconnect $device")
        connect(device)
                .retry(3, 100)
                .useAutoConnect(false)
                .enqueue()
        return connectionState()
    }

    override fun doDisconnect(): Observable<ConnectionState> {
        this.disconnect().enqueue()
        return connectionState()
    }
}