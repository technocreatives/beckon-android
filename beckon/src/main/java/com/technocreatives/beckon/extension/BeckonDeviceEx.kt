package com.technocreatives.beckon.extension

import arrow.core.Either
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.DeviceMetadata
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.checkNotifyList
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import java.util.UUID

data class BeckonState<State>(
    val metadata: DeviceMetadata,
    val connectionState: ConnectionState,
    val state: State
)

data class NoBeckonDeviceFoundException(val metadata: WritableDeviceMetadata) : Throwable()
data class NoSavedDeviceFoundException(val address: MacAddress) : Throwable()

fun <Change> BeckonDevice.changes(characteristicUUID: UUID, mapper: CharacteristicMapper<Change>): Observable<Change> {
    return changes().filter { it.uuid == characteristicUUID }
        .map { mapper(it) }
}

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
): Observable<BeckonState<State>> {
    val states = states(mapper, reducer, defaultState)
    return Observable.combineLatest(
        states,
        connectionStates(),
        BiFunction<State, ConnectionState, BeckonState<State>> { t1, t2 ->
            BeckonState(metadata(), t2, t1)
        })
}

fun <Change, State> BeckonDevice.subscribe(
    subscribes: List<Characteristic>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<BeckonState<State>> {
    return subscribe(subscribes).andThen(deviceStates(mapper, reducer, defaultState))
}

fun BeckonDevice.subscribe(subscribes: List<Characteristic>): Completable {
    return when (val list = checkNotifyList(subscribes, metadata().services, metadata().characteristics)) {
        is Either.Left -> Completable.error(list.a)
        is Either.Right -> subscribe(list.b)
    }
}
