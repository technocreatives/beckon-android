package com.technocreatives.beckon.rx2

import arrow.core.Either
import com.technocreatives.beckon.Change
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.ConnectionState
import com.technocreatives.beckon.Metadata
import com.technocreatives.beckon.State
import com.technocreatives.beckon.checkNotifyList
import io.reactivex.Completable
import io.reactivex.Observable
import java.util.UUID

data class BeckonState<State>(
    val metadata: Metadata,
    val connectionState: ConnectionState,
    val state: State
)

fun <Change> BeckonDeviceRx.changes(characteristicUUID: UUID, mapper: CharacteristicMapper<Change>): Observable<Change> {
    return changes().filter { it.uuid == characteristicUUID }
        .map { mapper(it) }
}

fun BeckonDeviceRx.deviceStates(): Observable<BeckonState<State>> {
    return Observable.combineLatest(
        states(),
        connectionStates(),
        { t1, t2 ->
            BeckonState(metadata(), t2, t1)
        }
    )
}

fun BeckonDeviceRx.subscribe(subscribes: List<Characteristic>): Completable {
    return when (val list = checkNotifyList(subscribes, metadata().services, metadata().characteristics)) {
        is Either.Left -> Completable.error(list.value.toException())
        is Either.Right -> subscribe(list.value)
    }
}
