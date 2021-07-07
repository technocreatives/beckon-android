package com.technocreatives.beckon.extensions

import arrow.core.Either
import arrow.core.computations.either
import com.technocreatives.beckon.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import no.nordicsemi.android.ble.data.Data
import java.util.*

fun <Change> BeckonDevice.changes(
    characteristicUUID: UUID,
    mapper: CharacteristicMapper<Change>
): Flow<Change> =
    changes().filter { it.uuid == characteristicUUID }
        .map { mapper(it) }

suspend fun BeckonDevice.read(char: Characteristic): Either<ReadDataError, Change> =
    either {
        val foundCharacteristic = metadata().findCharacteristic<FoundCharacteristic.Read>(char).bind()
        read(foundCharacteristic).bind()
    }

suspend fun BeckonDevice.write(data: Data, char: Characteristic): Either<WriteDataError, Change> =
    either {
        val foundCharacteristic = metadata().findCharacteristic<FoundCharacteristic.Write>(char).bind()
        write(data, foundCharacteristic).bind()
    }

suspend fun BeckonDevice.subscribe(char: Characteristic): Either<SubscribeDataError, Unit> =
    either {
        val foundCharacteristic = metadata().findCharacteristic<FoundCharacteristic.Notify>(char).bind()
        subscribe(foundCharacteristic).bind()
    }

suspend fun BeckonDevice.writeSplit(
    data: ByteArray,
    char: Characteristic
): Either<WriteDataError, SplitPackage> =
    either {
        val foundCharacteristic = metadata().findCharacteristic<FoundCharacteristic.Write>(char).bind()
        writeSplit(data, foundCharacteristic).bind()
    }
