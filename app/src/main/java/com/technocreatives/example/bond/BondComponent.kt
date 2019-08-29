package com.technocreatives.example.bond

import android.Manifest
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.example.App
import com.technocreatives.example.R
import com.technocreatives.example.bond.domain.BluetoothStateUseCase
import com.technocreatives.example.bond.domain.LocationPermissionStateUseCase
import com.technocreatives.example.bond.domain.ScanConditionUseCase
import com.technocreatives.example.bond.domain.ScanDeviceUseCase
import com.technocreatives.example.serviceUUID
import no.nordicsemi.android.support.v18.scanner.ScanSettings


class BondComponent(private val view: BondActivity) {
    private val permissions =
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    val appStore = createStore()
    val beckonClient by lazy { App[view].beckonClient() }
    val bluetoothUseCase = BluetoothStateUseCase(beckonClient)

    val locationPermissionUseCase = LocationPermissionStateUseCase(appStore)

    val scanConditionUseCase = ScanConditionUseCase(bluetoothUseCase, locationPermissionUseCase)
    val scanUseCase = ScanDeviceUseCase(beckonClient, scanConditionUseCase)

    val callbacks = LocationPermissionsCallbacks(view, permissions, appStore)
    val permissionsService = PermissionsService(view, permissions, view.getString(R.string.rationale), 2019, callbacks)

    val scanSettings = provideSetting()

    private fun provideSetting(): ScannerSetting {
        val settings = ScanSettings.Builder()
                .setLegacy(false)
                .setUseHardwareFilteringIfSupported(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(true)
                .build()

        val filters = listOf(DeviceFilter(deviceName = null, deviceAddress = null, serviceUuid = serviceUUID))
        return ScannerSetting(settings, filters)
    }
}

