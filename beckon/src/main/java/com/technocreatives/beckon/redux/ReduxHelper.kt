package com.technocreatives.beckon.redux

internal fun combineReducers(vararg reducers: Reducer): Reducer {
    return { state, action ->
        reducers.fold(state) { state, reducer ->
            reducer(state, action)
        }
    }
}

internal fun createBeckonStore(): Store {
    val reducer: Reducer = { state, action ->
        when (action) {
            is AddDevice -> state.copy(devices = addDevice(state.devices, action.device))
            is RemoveDevice -> state.copy(devices = removeDevice(state.devices, action.device))
        }
    }
    return BeckonStore(reducer, BeckonState(emptyList()))
}

private fun addDevice(devices: List<Device>, device: Device): List<Device> {
    return devices.plus(device)
}

private fun removeDevice(devices: List<Device>, device: Device): List<Device> {
    return devices.minus(device)
}
