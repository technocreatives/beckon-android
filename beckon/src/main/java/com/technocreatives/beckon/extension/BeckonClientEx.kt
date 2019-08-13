package com.technocreatives.beckon.extension

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.lenguyenthanh.rxarrow.concatMapSingleE
import com.lenguyenthanh.rxarrow.filterE
import com.lenguyenthanh.rxarrow.flatMapE
import com.lenguyenthanh.rxarrow.flatMapEither
import com.lenguyenthanh.rxarrow.flatMapObservableE
import com.lenguyenthanh.rxarrow.flatMapSingleE
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonResult
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.WritableDeviceMetadata
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import timber.log.Timber

sealed class BeckonError {
    class NoSaveDevice() : BeckonError()
    class NoConectedDevice() : BeckonError()
}

fun <Change, State> BeckonClient.deviceStates(
    address: MacAddress,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<Either<Throwable, BeckonState<State>>> {
    return findSavedDeviceE(address)
        .flatMapEither { findConnectedDeviceE(it) }
        .flatMapObservableE { it.deviceStates(mapper, reducer, defaultState) }
}

fun <Change, State> BeckonClient.deviceStates(
    addresses: List<String>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<List<Either<Throwable, BeckonState<State>>>> {
    val devices = addresses.map { deviceStates(it, mapper, reducer, defaultState) }
    Timber.d("deviceStates $devices")
    return Observable.combineLatest(devices) {
        Timber.d("combineLatest ${it[0]}")
        it.map { it as Either<Throwable, BeckonState<State>> }.toList()
    }
}

fun BeckonClient.findSavedDeviceE(macAddress: MacAddress): Single<Either<Throwable, WritableDeviceMetadata>> {
    return findSavedDevice(macAddress).map { it.right() as Either<Throwable, WritableDeviceMetadata> }
        .onErrorReturn { NoSavedDeviceFoundException(macAddress).left() }
}

fun BeckonClient.findConnectedDeviceE(metadata: WritableDeviceMetadata): Single<Either<Throwable, BeckonDevice>> {
    return findConnectedDevice(metadata.macAddress)
        .map { it.right() as Either<Throwable, BeckonDevice> }
        .onErrorReturn { NoBeckonDeviceFoundException(metadata).left() }
}

// onError can be happen
fun BeckonClient.scanAndConnect(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<BeckonResult<BeckonDevice>> {
    return scan(conditions, setting)
        .concatMapSingleE { connect(it, descriptor) }
        .doOnNext { Timber.d("scanAndConnect $it") }
}

fun BeckonClient.scan(conditions: Observable<Boolean>, setting: ScannerSetting): Observable<BeckonResult<ScanResult>> {
    return conditions
        .switchMap {
            Timber.d("Scan switchMap $it")
            if (it) {
                startScan(setting)
            } else {
                stopScan()
                Observable.empty()
            }
        }
        .distinct { it.macAddress }
        .map { it.right() as BeckonResult<ScanResult> }
        .onErrorReturn { it.left() }
        .doOnNext { Timber.d("scan $it") }
        .doOnDispose {
            Timber.d("doOnDispose stop scan")
            stopScan()
        }
}

// onError can be happen
fun <Change, State> BeckonClient.scanAndSave(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State,
    filter: (State) -> Boolean
): Observable<BeckonResult<MacAddress>> {
    return scanAndConnect(conditions, setting, descriptor)
        .flatMapE { it.deviceStates(mapper, reducer, defaultState) }
        .doOnNext { Timber.d("Scan device state $it") }
        .filterE { deviceState -> filter(deviceState.state) }
        .flatMapSingleE { save(it.metadata.macAddress) }
        .doOnNext { Timber.d("scanAndSave $it") }
}
