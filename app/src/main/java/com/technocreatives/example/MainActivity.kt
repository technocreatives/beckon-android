package com.technocreatives.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.states
import com.technocreatives.example.common.extension.disposedBy
import com.technocreatives.example.common.view.init
import com.technocreatives.example.common.view.verticalLayoutManager
import com.technocreatives.example.domain.ScanAndConnectDeviceUseCase
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private val bag = CompositeDisposable()

    private val beckon by lazy { App[this].beckonClient() }

    private val scanDeviceUseCase by lazy { ScanAndConnectDeviceUseCase(beckon) }

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device $it")
//            beckon.disconnect(it.deviceInfo())
        }
    }

    private val discoveredAdapter by lazy {
        ScanResultAdapter(layoutInflater) {
            Timber.d("Connect to ${it.macAddress}")
            beckon.connect(it, characteristics)
                    .subscribe { device ->
                        update(device)
                        when (device.success()) {
                            true -> {
                                Timber.d("success $device")
                                beckon.findDevice(device.macAddress)
                                        .flatMapObservable { it.changes() }
                                        .subscribe { change ->
                                            Timber.d("Device state changes $change")
                                        }

                                val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
                                beckon.findDevice(device.macAddress)
                                        .flatMapObservable { it.states(mapper, reducer, defaultState) }
                                        .subscribe {
                                            Timber.d("new State $it")
                                        }
                            }
                            false -> Timber.d("failure $device")
                        }
                    }

        }
    }

    private fun update(device: DeviceMetadata) {
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

        val filters = listOf(DeviceFilter(deviceName = "AXKID", deviceAddress = null, serviceUuid = null))

        beckon.startScan(ScannerSetting(settings, filters))

        scanDeviceUseCase.execute(characteristics)
                .doOnNext { Timber.d("Found $it") }
                .flatMap { beckon.save(it.metadata.macAddress).toObservable() }
                .subscribe(
                        { result -> Timber.d("Save device correctly") },
                        { error -> Timber.e(error, "Save device error") },
                        { Timber.d("completed") }
                )
                .disposedBy(bag)
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

