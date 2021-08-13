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

suspend fun BeckonDevice.read(char: Characteristic): Either<BeckonActionError, Change> =
    either {
        val foundCharacteristic =
            metadata().findCharacteristic<FoundCharacteristic.Read>(char).bind()
        read(foundCharacteristic)
            .mapLeft { BleActionError(it.macAddress, it.uuid, it.status, Property.READ) }
            .bind()
    }

suspend fun BeckonDevice.write(
    data: Data,
    char: Characteristic
): Either<BeckonActionError, Change> =
    either {
        val foundCharacteristic =
            metadata().findCharacteristic<FoundCharacteristic.Write>(char).bind()
        write(data, foundCharacteristic)
            .mapLeft { BleActionError(it.macAddress, it.uuid, it.status, Property.WRITE) }
            .bind()
    }

suspend fun BeckonDevice.subscribe(char: Characteristic): Either<BeckonActionError, Unit> =
    either {
        val foundCharacteristic =
            metadata().findCharacteristic<FoundCharacteristic.Notify>(char).bind()
        subscribe(foundCharacteristic)
            .mapLeft { BleActionError(it.macAddress, it.uuid, it.status, Property.NOTIFY) }
            .bind()
    }

suspend fun BeckonDevice.writeSplit(
    data: ByteArray,
    char: Characteristic
): Either<BeckonActionError, SplitPackage> =
    either {
        val foundCharacteristic =
            metadata().findCharacteristic<FoundCharacteristic.Write>(char).bind()
        writeSplit(data, foundCharacteristic)
            .mapLeft { BleActionError(it.macAddress, it.uuid, it.status, Property.WRITE) }
            .bind()
    }

fun BeckonDevice.getMaximumPacketSize(): Int =
    mtu().value - 3