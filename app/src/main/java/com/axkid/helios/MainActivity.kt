package com.axkid.helios

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.axkid.helios.common.view.init
import com.axkid.helios.common.view.verticalLayoutManager
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.disposedBy
import com.technocreatives.beckon.states
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private val bag = CompositeDisposable()

    private val beckon by lazy { App[this].beckonClient() }

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device $it")
            beckon.disconnect(it)
        }
    }

    private val discoveredAdapter by lazy {
        ScanResultAdapter(layoutInflater) {
            Timber.d("Connect to ${it.macAddress}")
            val device = beckon.connect(it, characteristics, true)

            device.changes().subscribe {
                Timber.d("Device state changes $it")
            }

            val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
            device.states(mapper, reducer, defaultState)
                    .subscribe {
                        Timber.d("new State $it")
                    }
        }
    }

    private fun update(devices: List<BeckonDevice>) {
        // Timber.d("managers $managers")
        // Timber.d("manager ${managers.map { it.peripheral }}")
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

        val filters = listOf(ScanFilter.Builder()
                .setDeviceName("AXKID").build())

        beckon.scanList(ScannerSetting(settings, filters))
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    discoveredAdapter.items = it
                    Timber.d("Scan result $it")
                }.disposedBy(bag)

        beckon.devices()
                .map { it.map { address -> beckon.findDevice(address) } }
                .map { it.filter { device -> device != null } }
                .map { it.map { device -> device!! } }
                .subscribe {
                    Timber.d("All devices $it")
                    update(it)
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
        val device = client.findDevice(macAddress) ?: return Observable.empty()

        return device
                .changes()
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

