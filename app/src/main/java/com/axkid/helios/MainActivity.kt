package com.axkid.helios

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.axkid.helios.common.view.init
import com.axkid.helios.common.view.verticalLayoutManager
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private var disposable: Disposable? = null

    private val beckon by lazy { BeckonClient.create(this) }
    private val managers = mutableListOf<BeckonDevice>()

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device ${it.macAddress()}")
            beckon.disconnect(it)
        }
    }

    private val discoveredAdapter by lazy {
        ScanResultAdapter(layoutInflater) {
            Timber.d("Connect to ${it.macAddress}")
            val device = beckon.connect(it, characteristics, false)

            device.changes().subscribe {
                Timber.d("Device state changes $it")
            }

            states(device, mapper, reducer)
                    .subscribe {
                        Timber.d("new State $it")
                    }
            managers.add(device)
            update(managers)
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
        Timber.plant(Timber.DebugTree())
        bindView()

        val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setUseHardwareFilteringIfSupported(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(false)
                .build()

        val filters = listOf(ScanFilter.Builder()
                .setDeviceName("AXKID").build())

        disposable = beckon.scanList(ScannerSetting(settings, filters))
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    discoveredAdapter.items = it
                    Timber.d("Scan result $it")
                }
    }

    private fun bindView() {
        rvDiscoveredDevices.init(discoveredAdapter, verticalLayoutManager())
        rvConnectedDevices.init(connectedAdapter, verticalLayoutManager())
    }

    override fun onStop() {
        disposable?.dispose()
        super.onStop()
    }

    fun states(client: BeckonClient, macAddress: String, mapper: CharacteristicMapper<AxkidChange>, reducer: BiFunction<AxkidState, AxkidChange, AxkidState>): Observable<AxkidState> {

        val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
        val device = client.getDevice(macAddress) ?: return Observable.empty()

        return device
                .changes()
                .map { mapper(it) }
                .scan(defaultState, reducer)
    }

    fun states(device: BeckonDevice, mapper: CharacteristicMapper<AxkidChange>, reducer: BiFunction<AxkidState, AxkidChange, AxkidState>): Observable<AxkidState> {
        val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
        return device
                .changes()
                .map { mapper(it) }
                .scan(defaultState, reducer)
    }
}

