package com.technocreatives.beckon.internal

import android.content.Context
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonScanResult
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceChange
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScanFailureException
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.redux.AddDevice
import com.technocreatives.beckon.redux.Device
import com.technocreatives.beckon.redux.RemoveDevice
import com.technocreatives.beckon.redux.createBeckonStore
import io.reactivex.Observable
import io.reactivex.Single
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import timber.log.Timber

internal class BeckonClientImpl(private val context: Context) : BeckonClient {

    private val connectedDevices: MutableMap<MacAddress, BeckonDevice> = HashMap()
    private val beckonStore by lazy { createBeckonStore() }

    override fun scan(setting: ScannerSetting): Observable<BeckonScanResult> {
        val scanner = BluetoothLeScannerCompat.getScanner()
        return Observable.create<BeckonScanResult> { observer ->
            val callback: ScanCallback = object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    Timber.d("onScanFailed $errorCode")
                    observer.onError(ScanFailureException(errorCode))
                }

                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    Timber.d("onScanResult $callbackType $result")
                    observer.onNext(BeckonScanResult(result.device, result.rssi))
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    Timber.d("onBatchScanResults $results")
                    results.forEach { result ->
                        observer.onNext(BeckonScanResult(result.device, result.rssi))
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

    override fun scanList(setting: ScannerSetting): Observable<List<BeckonScanResult>> {
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

    override fun getDevice(macAddress: String): BeckonDevice? {
        return connectedDevices[macAddress]
    }

    override fun devices(): Observable<List<MacAddress>> {
        return beckonStore.states().map { it.devices }.map { it.map { device -> device.address } }
    }

    override fun getDevices(): List<BeckonDevice> {
        return connectedDevices.values.toList()
    }

    override fun states(): Observable<List<DeviceChange>> = TODO("Need to be implemented later")
//    {
//        return devices().flatMapIterable { it }
//                .map { it to getDevice(it) }
//                .filter { it.second != null }
//    }

    override fun saveDevices(devices: List<BeckonDevice>): Single<Boolean> = TODO("Need to be implemented later")

    override fun connect(result: BeckonScanResult, characteristics: List<Characteristic>, autoConnect: Boolean): BeckonDevice {
        Timber.d("Connect $result")
        // Prevent from calling again when called again (screen orientation changed)
        return connectedDevices[result.device.address]
                ?: doConnect(result, characteristics, autoConnect)
    }

    private fun doConnect(result: BeckonScanResult, characteristics: List<Characteristic>, autoConnect: Boolean): BeckonDevice {
        val beckonDevice = BeckonDeviceImpl(context, result.device, characteristics)
        connectedDevices[result.macAddress] = beckonDevice
        beckonDevice.doConnect(autoConnect)
        beckonStore.dispatch(AddDevice(Device(result.macAddress)))
        return beckonDevice
    }

    override fun disconnect(device: BeckonDevice): Observable<ConnectionState> {
        beckonStore.dispatch(RemoveDevice(Device(device.macAddress())))
        return device.doDisconnect()
    }
}
