package com.technocreatives.example.bond

import com.lenguyenthanh.redux.core.Reducer
import com.lenguyenthanh.redux.rx2.RxStore
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.example.bond.domain.LocationPermissionState

sealed class BondAction {
    data class UpdateBluetoothState(val state: BluetoothState) : BondAction()
    data class UpdateLocationPermissionState(val state: LocationPermissionState) : BondAction()
}

private val reducer: Reducer<AppState, BondAction> = { state, action ->
    when (action) {
        is BondAction.UpdateBluetoothState -> state.copy(bluetoothState = action.state)
        is BondAction.UpdateLocationPermissionState -> state.copy(locationPermissionState = action.state)
    }
}
typealias AppStore = RxStore<AppState, BondAction>

fun createStore(): AppStore {
    return RxStore(reducer, { AppState(BluetoothState.UNKNOWN, LocationPermissionState.UNKNOWN) })
}

data class AppState(
        val bluetoothState: BluetoothState,
        val locationPermissionState: LocationPermissionState
)