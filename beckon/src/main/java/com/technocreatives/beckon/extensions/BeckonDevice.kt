package com.technocreatives.beckon.extensions

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonState
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.State
import io.reactivex.Flowable.combineLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.*

fun <Change> BeckonDevice.changes(
    characteristicUUID: UUID,
    mapper: CharacteristicMapper<Change>
): Flow<Change> =
    changes().filter { it.uuid == characteristicUUID }
        .map { mapper(it) }