package com.axkid.helios

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.axkid.helios.common.view.init
import com.axkid.helios.common.view.verticalLayoutManager
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.DiscoveredDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.util.disposedBy
import com.technocreatives.beckon.states
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private val bag = CompositeDisposable()

    private val beckon by lazy { App[this].beckonClient() }

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device $it")
            beckon.disconnect(it.deviceInfo())
        }
    }

    private val discoveredAdapter by lazy {
        ScanResultAdapter(layoutInflater) {
            Timber.d("Connect to ${it.macAddress}")
            beckon.connect(it, characteristics)
                    .subscribe { device ->
                        update(device)
                        when (device) {
                            is DiscoveredDevice.SuccessDevice -> {
                                Timber.d("success $device")
                                beckon.findDevice(device.info.macAddress)
                                        .flatMap { it.changes() }
                                        .subscribe { change ->
                                            Timber.d("Device state changes $change")
                                        }

                                val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
                                beckon.findDevice(device.info.macAddress)
                                        .flatMap { it.states(mapper, reducer, defaultState) }
                                        .subscribe {
                                            Timber.d("new State $it")
                                        }
                            }
                            is DiscoveredDevice.FailureDevice -> Timber.d("failure $device")
                        }
                    }

        }
    }

    private fun update(device: DiscoveredDevice) {
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

        val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setUseHardwareFilteringIfSupported(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build()

        val filters = listOf(DeviceFilter(deviceName = "AXKID", deviceAddress = null))

        beckon.scanList(ScannerSetting(settings, filters))
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    discoveredAdapter.items = it
                    Timber.d("Scan result $it")
                }.disposedBy(bag)

    }

    private fun bindView() {
        rvDiscoveredDevices.init(discoveredAdapter, verticalLayoutManager())
        rvConnectedDevices.init(connectedAdapter, verticalLayoutManager())
    }

    override fun onStop() {
        bag.clear()
        super.onStop()
    }

    fun states(client: BeckonClient, macAddress: String, mapper: CharacteristicMapper<AxkidChange>, reducer: BiFunction<AxkidState, AxkidChange, AxkidState>): Observable<AxkidState> {

        val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
        val device = client.findDevice(macAddress)

        return device.flatMap { it.changes() }
                .map { mapper(it) }
                .scan(defaultState, reducer)
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

