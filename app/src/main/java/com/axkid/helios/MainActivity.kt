package com.axkid.helios

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.axkid.helios.common.view.init
import com.axkid.helios.common.view.verticalLayoutManager
import com.axkid.helios.feature.test.DeviceAdapter
import com.technocreatives.beckon.Beckon
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.ScannerSetting
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_test.*
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import timber.log.Timber

typealias SeatDevice = BeckonDevice<Changes, DeviceState>

class MainActivity : AppCompatActivity() {

    private var disposable: Disposable? = null

    private val beckon by lazy { Beckon(this) }
    private val managers = mutableListOf<SeatDevice>()

    private val connectedAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Disconnect device ${it.address}")
            beckon.disConnect(it)
        }
    }

    private val discoveredAdapter by lazy {
        DeviceAdapter(layoutInflater) {
            Timber.d("Connect to ${it.address}")
            val manager = beckon.connect(it, factory = factory) as SeatDevice

            manager.states().subscribe {
                Timber.d("Device state changes $it")
            }
            managers.add(manager)
            update(managers)
        }
    }

    private fun update(managers: List<SeatDevice>) {
        // Timber.d("managers $managers")
        // Timber.d("manager ${managers.map { it.peripheral }}")
        connectedAdapter.items = managers.map { it.device }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        bindView()

        val settings = ScanSettings.Builder()
            .setLegacy(false)
            .setUseHardwareFilteringIfSupported(false)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setUseHardwareBatchingIfSupported(false)
            .build()

        val filters = listOf(ScanFilter.Builder()
            .setDeviceName("AXKID").build())

        disposable = beckon.scanList(ScannerSetting(filters, settings))
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                discoveredAdapter.items = it.map { it.device }
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
}
