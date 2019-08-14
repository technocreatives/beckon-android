package com.technocreatives.example.bond.domain

import com.technocreatives.beckon.BluetoothState
import io.reactivex.Observable
import io.reactivex.functions.BiFunction

enum class ScanCondition {
    BLUETOOTH_OFF,
    NO_LOCATION_PERMISSION,
    OK
}

class ScanConditionUseCase(
    val bluetoothUseCase: BluetoothStateUseCase,
    val locationUseCase: LocationPermissionStateUseCase
) {
    operator fun invoke(): Observable<ScanCondition> {
        return Observable.combineLatest(
            locationUseCase(), bluetoothUseCase(),
            BiFunction<LocationPermissionState, BluetoothState, ScanCondition> { l, b ->
                when {
                    b != BluetoothState.ON -> ScanCondition.BLUETOOTH_OFF
                    l != LocationPermissionState.ON -> ScanCondition.NO_LOCATION_PERMISSION
                    else -> ScanCondition.OK
                }
            })
    }
}