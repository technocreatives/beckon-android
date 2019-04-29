package com.technocreatives.beckon

import android.bluetooth.BluetoothDevice
import android.content.Context
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

class Beckon(private val context: Context) {

    private val connectedDevices: MutableMap<String, BeckonDevice<Any, Any>> = HashMap()
    private val subject: PublishSubject<List<DiscoveredPeripheral>> = PublishSubject.create()
    private val callbacks = BeckonManagerCallbacks()

    fun scan(setting: ScannerSetting): Observable<DiscoveredPeripheral> {
        val scanner = BluetoothLeScannerCompat.getScanner()
        return Observable.create<DiscoveredPeripheral> { observer ->
            val callback: ScanCallback = object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    Timber.d("onScanFailed $errorCode")
                    observer.onError(ScanFailureException(errorCode))
                }

                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    Timber.d("onScanResult $callbackType $result")
                    observer.onNext(DiscoveredPeripheral(result.device, result.rssi))
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    Timber.d("onBatchScanResults $results")
                    results.forEach { result ->
                        observer.onNext(DiscoveredPeripheral(result.device, result.rssi))
                    }
                }
            }

            Timber.d("Bluetooth scanning starting")

            scanner.startScan(setting.filters, setting.settings, callback)

            observer.setCancellable {
                scanner.stopScan(callback)
                Timber.d("Bluetooth scanning stopped")
            }
        }
    }

    fun states(): Observable<Unit> {
        return Observable.empty()
    }

    fun scanList(setting: ScannerSetting): Observable<List<DiscoveredPeripheral>> {
        // need to filter connected devices or saved devices
        return scan(setting)
            .doOnNext { Timber.d("New Device $it") }
            .scan(emptyList(),
                { t1, t2 ->
                    if (t1.any { it.device.address == t2.device.address }) {
                        t1
                    } else {
                        t1.plus(t2)
                    }
                }
            )
    }

    fun connect(device: BluetoothDevice, factory: BeckonDeviceFactory<Any, Any>): BeckonDevice<Any, Any> {
        Timber.d("Connect $device")
        // Prevent from calling again when called again (screen orientation changed)
        val manager = connectedDevices.get(device.address)
        if (manager == null) {
            val beckonDevice = factory(context, device, callbacks)
            connectedDevices[device.address] = beckonDevice
            beckonDevice.doConnect()
            return beckonDevice
        }
        return manager
    }

    fun disConnect(device: BluetoothDevice) {
        Timber.d("disConnect $device ${connectedDevices.get(device.address)}")
        if (connectedDevices.get(device.address) != null) {
            val manager = connectedDevices.get(device.address)
            manager?.doDisconnect()
        }
    }

    fun connectedDevices(): Observable<List<DiscoveredPeripheral>> {
        return subject.hide()
    }

    // fun States() : Observable<List<Device, State>>
    // fun States(filter: Filter<State>) : Observable<List<Device, State>>
    // fun states(device: Device): Observable<State>
}
