package com.technocreatives.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.Property
import com.technocreatives.beckon.Requirement
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.extension.deviceStates
import com.technocreatives.example.common.extension.toUuid
import com.technocreatives.example.common.view.init
import com.technocreatives.example.common.view.verticalLayoutManager
import com.technocreatives.example.domain.ScanDeviceUseCase
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private val bag = CompositeDisposable()

    private val beckon by lazy { App[this].beckonClient() }

    private val scanDeviceUseCase by lazy { ScanDeviceUseCase(beckon) }

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device $it")
//            beckon.disconnect(it.deviceInfo())
        }
    }

    private val discoveredAdapter by lazy {
        ScanResultAdapter(layoutInflater) {
            Timber.d("Connect to ${it.macAddress}")

            val requirements = listOf(
                    Requirement(seatUuId.toUuid(), serviceUUID.toUuid(), Property.NOTIFY),
                    Requirement(temperatureUuid.toUuid(), serviceUUID.toUuid(), Property.NOTIFY)
            )
            val subscribeList = listOf(
                    Characteristic(seatUuId.toUuid(), serviceUUID.toUuid()),
                    Characteristic(temperatureUuid.toUuid(), serviceUUID.toUuid())
            )
            val descriptor = Descriptor(requirements, subscribeList)

            Log.d(TAG, "Connecting to: $it")
            beckon.connect(it, descriptor)
                    .subscribe { device ->
                        update(device.metadata())
                        Timber.d("success $device")
                        device.changes()
                                .subscribe { change ->
                                    Timber.d("Device state changes $change")
                                }

                        val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
                        device.deviceStates(mapper, reducer, defaultState)
                                .subscribe {
                                    Timber.d("new State $it")
                                }
                    }

        }
    }

    private fun update(device: Metadata) {
        // Timber.d("managers $managers")
        // Timber.d("manager ${managers.map { it.peripheral }}")
        val devices = connectedAdapter.items + device
        connectedAdapter.items = devices
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        bindView()

        startBluetoothService()

    }

    private fun bindView() {
        rvDiscoveredDevices.init(discoveredAdapter, verticalLayoutManager())
        rvConnectedDevices.init(connectedAdapter, verticalLayoutManager())
    }

    override fun onResume() {
        super.onResume()
        val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setUseHardwareFilteringIfSupported(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build()

        val filters = listOf(DeviceFilter(name = "AXKID"))

        beckon.startScan(ScannerSetting(settings, filters))

//        scanDeviceUseCase(characteristics)
//                .doOnNext { Timber.d("Found $it") }
//                .flatMap { beckon.save(it.metadata.macAddress).toObservable() }
//                .subscribe(
//                        { result -> Timber.d("Save device correctly") },
//                        { error -> Timber.e(error, "Save device error") },
//                        { Timber.d("completed") }
//                )
//                .disposedBy(bag)
    }

    override fun onPause() {
        beckon.stopScan()
        super.onPause()
    }

    override fun onStop() {
        bag.clear()
        super.onStop()
    }

    private fun startBluetoothService() {
        val intent = Intent(this, BluetoothService::class.java)

        // Check version and start in foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

