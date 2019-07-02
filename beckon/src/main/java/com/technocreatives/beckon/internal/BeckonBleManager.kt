package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.technocreatives.beckon.BondState
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicFailedException
import com.technocreatives.beckon.CharacteristicResult
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.CreateBondFailedException
import com.technocreatives.beckon.ReadDataEXception
import com.technocreatives.beckon.RemoveBondFailedException
import com.technocreatives.beckon.Type
import com.technocreatives.beckon.WriteDataException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.SingleSubject
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.DataSentCallback
import no.nordicsemi.android.ble.data.Data
import timber.log.Timber

internal class BeckonBleManager(
    context: Context,
    private val characteristics: List<Characteristic>
) : BleManager<BeckonManagerCallbacks>(context) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val stateSubject: BehaviorSubject<ConnectionState> =
        BehaviorSubject.createDefault(ConnectionState.NotStarted)
    private val bondStateSubject: BehaviorSubject<BondState> = BehaviorSubject.createDefault(BondState.NotBonded)
    private val changeSubject = PublishSubject.create<Change>()
    private val discoveredDevice by lazy { SingleSubject.create<List<CharacteristicResult>>() }
    private val bondSubject by lazy {
        PublishSubject.create<Boolean>()
    }

    init {
        mCallbacks = BeckonManagerCallbacks(stateSubject, bondStateSubject)
    }

    fun connect(request: ConnectRequest): Single<List<CharacteristicResult>> {
        request.enqueue()
        return discoveredDevice.hide()
    }

    private val gattCallback = object : BleManagerGattCallback() {

        override fun initialize() {
            Timber.d("initialize")
            bluetoothGatt?.let { gatt ->
                val result = characteristics.flatMap { findBluetoothGattCharacteristic(gatt, it) }
                    .map { setupCallback(it) }
                discoveredDevice.onSuccess(result)
            }
        }

        private fun setupCallback(result: CharacteristicResult): CharacteristicResult {
            if (result is CharacteristicResult.Notify) {
                setupNotificationCallback(result)
            }
            return result
        }

        private fun setupNotificationCallback(success: CharacteristicResult.Notify): CharacteristicResult {
            Timber.d("setNotification callback $success")
            val callback = DataReceivedCallback { device, data ->
                Timber.d("DataReceivedCallback $device $data")
                changeSubject.onNext(Change(success.characteristic, data))
            }
            readCharacteristic(success.gatt).with(callback).enqueue()
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
        ): List<CharacteristicResult> {

            Timber.d("isRequiredServiceSupported $gatt $characteristic")

            val service = gatt.getService(characteristic.service)

            return if (service != null) {
                Timber.d("All characteristic $service ${service.characteristics.map { it.descriptors + " " + it.uuid }}")

                val bluetoothGattCharacteristic = service.getCharacteristic(characteristic.uuid)
                if (bluetoothGattCharacteristic == null) {
                    listOf(
                        CharacteristicResult.Failed(
                            characteristic,
                            CharacteristicFailedException("BluetoothGatt not found!")
                        )
                    )
                } else {
                    checkRequiredServiceSupported(characteristic, bluetoothGattCharacteristic)
                }
            } else {
                listOf(
                    CharacteristicResult.Failed(
                        characteristic,
                        CharacteristicFailedException("Service ${characteristic.service} not found!")
                    )
                )
            }
        }
    }

    private fun checkRequiredServiceSupported(
        characteristic: Characteristic,
        bluetoothGattCharacteristic: BluetoothGattCharacteristic
    ): List<CharacteristicResult> {
        val rxProperties = bluetoothGattCharacteristic.properties

        return characteristic.types.map {
            if (it == Type.NOTIFY) {
                val notifyAbility = rxProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0
                if (notifyAbility) {
                    CharacteristicResult.Notify(characteristic, bluetoothGattCharacteristic)
                } else {
                    CharacteristicResult.Failed(
                        characteristic,
                        CharacteristicFailedException("This characteristic does not support notify!")
                    )
                }
            } else if (it == Type.READ) {
                val readAbility = rxProperties and BluetoothGattCharacteristic.PROPERTY_READ > 0
                if (readAbility) {
                    CharacteristicResult.Read(characteristic, bluetoothGattCharacteristic)
                } else {
                    CharacteristicResult.Failed(
                        characteristic,
                        CharacteristicFailedException("This characteristic does not support read!")
                    )
                }
            } else {
                val writeAbility = rxProperties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0
                if (writeAbility) {
                    CharacteristicResult.Write(characteristic, bluetoothGattCharacteristic)
                } else {
                    CharacteristicResult.Failed(
                        characteristic,
                        CharacteristicFailedException("This characteristic does not support write!")
                    )
                }
            }
        }
    }

    fun doCreateBond(): Completable {
        bondStateSubject.onNext(BondState.CreatingBond)
        return Completable.create { emitter ->
            createBond()
                .done { emitter.onComplete() }
                .fail { device, status -> emitter.onError(CreateBondFailedException(device.address, status)) }
                .enqueue()
        }
    }

    fun doRemoveBond(): Completable {
        bondStateSubject.onNext(BondState.RemovingBond)

        return Completable.create { emitter ->
            removeBond()
                .done { emitter.onComplete() }
                .fail { device, status -> emitter.onError(RemoveBondFailedException(device.address, status)) }
                .enqueue()
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return gattCallback
    }

    fun connectionState(): Observable<ConnectionState> {
        return stateSubject.hide()
    }

    fun bondStates(): Observable<BondState> {
        return bondStateSubject.hide()
    }

    fun changes(): Observable<Change> {
        return changeSubject.hide()
    }

    fun currentState(): ConnectionState {
        return stateSubject.value!!
    }

    fun write(data: Data, characteristic: Characteristic, gatt: BluetoothGattCharacteristic): Single<Change> {
        return Single.create { emitter ->
            val callback = DataSentCallback { device, data ->
                Timber.d("write DataSentCallback characteristic: $characteristic device: $device data: $data")
                emitter.onSuccess(Change(characteristic, data))
            }
            writeCharacteristic(gatt, data)
                .with(callback)
                .fail { device, status ->
                    emitter.onError(
                        WriteDataException(
                            device.address,
                            status,
                            characteristic
                        )
                    )
                }
                .enqueue()
        }
    }

    fun read(characteristic: Characteristic, gatt: BluetoothGattCharacteristic): Single<Change> {
        return Single.create { emitter ->
            val callback = DataReceivedCallback { device, data ->
                Timber.d("read DataReceivedCallback characteristic: $characteristic device: $device data: $data")
                emitter.onSuccess(Change(characteristic, data))
            }
            readCharacteristic(gatt)
                .with(callback)
                .fail { device, status ->
                    emitter.onError(
                        ReadDataEXception(
                            device.address,
                            status,
                            characteristic
                        )
                    )
                }
                .enqueue()
        }
    }

    override fun log(priority: Int, message: String) {
        Timber.d(message)
    }

    override fun toString(): String {
        return "Connection state: ${stateSubject.value}, bond State: ${bondStateSubject.value}"
    }
}