package com.technocreatives.beckon

import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import timber.log.Timber

fun <Change, State> BeckonDevice.states(
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<State> {
    return this.changes()
        .map { mapper(it) }
        .scan(defaultState, reducer)
}

fun <Change, State> BeckonDevice.deviceStates(
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<DeviceState<State>> {
    val states = states(mapper, reducer, defaultState)
    return Observable.combineLatest(
        states,
        this.connectionStates(),
        BiFunction<State, ConnectionState, DeviceState<State>> { t1, t2 -> DeviceState(this.metadata(), t2, t1) })
}

data class DeviceState<State>(val metadata: DeviceMetadata, val connectionState: ConnectionState, val state: State)

fun <Change, State> BeckonClient.deviceStates(
    addresses: List<String>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<List<DeviceState<State>>> {
    val devices = addresses.map { address -> this.findDevice(address).flatMap { it.deviceStates(mapper, reducer, defaultState) } }
    Timber.d("deviceStates $devices")
    return Observable.combineLatest(devices) {
        Timber.d("combineLatest ${it.get(0)}")
        it.map { it as DeviceState<State> }.toList()
    }
}
