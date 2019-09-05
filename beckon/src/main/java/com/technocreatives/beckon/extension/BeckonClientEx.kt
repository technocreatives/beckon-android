package com.technocreatives.beckon.extension

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.lenguyenthanh.rxarrow.filterE
import com.lenguyenthanh.rxarrow.flatMapE
import com.lenguyenthanh.rxarrow.flatMapObservableEither
import com.lenguyenthanh.rxarrow.flatMapSingleE
import com.lenguyenthanh.rxarrow.flatMapSingleEither
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BeckonException
import com.technocreatives.beckon.BeckonResult
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.DeviceFilter
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.State
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import timber.log.Timber

fun <Change, State> BeckonClient.deviceStates(
    address: MacAddress,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<Either<BeckonDeviceError, BeckonState<State>>> {
    return findSavedDeviceE(address)
            .flatMapObservableEither { findConnectedDeviceO(it) }
            .flatMapE { it.deviceStates(mapper, reducer, defaultState) }
}

fun <Change, State> BeckonClient.deviceStates(
    addresses: List<String>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<List<Either<BeckonDeviceError, BeckonState<State>>>> {
    val devices = addresses.map { deviceStates(it, mapper, reducer, defaultState) }
    Timber.d("deviceStates $devices")
    return Observable.combineLatest(devices) {
        Timber.d("combineLatest ${it[0]}")
        it.map { it as Either<BeckonDeviceError, BeckonState<State>> }.toList()
    }
}

fun BeckonClient.deviceStates(address: MacAddress): Observable<Either<BeckonDeviceError, BeckonState<State>>> {
    return findSavedDeviceE(address)
            .flatMapObservableEither { findConnectedDeviceO(it) }
            .flatMapE { it.deviceStates() }
}

fun BeckonClient.devicesStates(addresses: List<String>): Observable<List<Either<BeckonDeviceError, BeckonState<State>>>> {
    if (addresses.isEmpty()) {
        return Observable.never()
    }
    val devices = addresses.map { deviceStates(it) }
    Timber.d("deviceStates $devices")
    return Observable.combineLatest(devices) {
        Timber.d("combineLatest without State ${it[0]}")
        it.map { it as Either<BeckonDeviceError, BeckonState<State>> }.toList()
    }
}

fun BeckonClient.findSavedDeviceE(macAddress: MacAddress): Single<Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata>> {
    return findSavedDevice(macAddress).map { it.right() as Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata> }
            .onErrorReturn { BeckonDeviceError.SavedDeviceNotFound(macAddress).left() }
}

// onError can be happen
fun BeckonClient.scanAndConnect(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<BeckonResult<BeckonDevice>> {

    val searchStream = search(conditions, setting.filters, descriptor).map { it.mapLeft { BeckonException(it) } }
            .doOnNext { Timber.d("SearchStream $it") }
    val scanStream = scan(conditions, setting)
        .flatMapSingleEither { safeConnect(it, descriptor) }

    return Observable.merge(scanStream, searchStream)
}

fun BeckonClient.safeConnect(result: ScanResult, descriptor: Descriptor): Single<BeckonResult<BeckonDevice>> {
    return connect(result, descriptor).map { it.right() as BeckonResult<BeckonDevice> }
            .onErrorReturn { it.left() }
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

fun BeckonClient.search(conditions: Observable<Boolean>, filters: List<DeviceFilter>, descriptor: Descriptor): Observable<Either<ConnectionError, BeckonDevice>> {
    return conditions
        .switchMap {
            Timber.d("Search switchMap $it")
            if (it) {
                search(filters, descriptor)
            } else {
                Observable.empty()
            }
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

fun BeckonClient.scanAndSave(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor,
    filter: (State) -> Boolean
): Observable<BeckonResult<MacAddress>> {
    return scanAndConnect(conditions.distinctUntilChanged(), setting, descriptor)
            .doOnNext { Timber.d("scanAndConnect $it") }
            .flatMapE { it.deviceStates() }
            .doOnNext { Timber.d("Scan device state $it") }
            .filterE { deviceState -> filter(deviceState.state) }
            .flatMapSingleE { save(it.metadata.macAddress) }
            .doOnNext { Timber.d("scanAndSave $it") }
}
