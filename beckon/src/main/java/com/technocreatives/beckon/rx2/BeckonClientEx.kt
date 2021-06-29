package com.technocreatives.beckon.rx2

import arrow.core.Either
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import com.lenguyenthanh.rxarrow.filterZ
import com.lenguyenthanh.rxarrow.flatMapObservableEither
import com.lenguyenthanh.rxarrow.flatMapSingleEither
import com.lenguyenthanh.rxarrow.flatMapSingleZ
import com.lenguyenthanh.rxarrow.flatMapZ
import com.lenguyenthanh.rxarrow.z
import com.technocreatives.beckon.BeckonDeviceError
import com.technocreatives.beckon.BeckonException
import com.technocreatives.beckon.BeckonResult
import com.technocreatives.beckon.ConnectionError
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.SavedMetadata
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.State
import io.reactivex.Observable
import io.reactivex.Single
import timber.log.Timber

fun BeckonClientRx.devicesStates(addresses: List<String>): Observable<List<Either<BeckonDeviceError, BeckonState<State>>>> {
    Timber.d("deviceStates $addresses")
    if (addresses.isEmpty()) {
        return Observable.never()
    }
    val devices = addresses.map { deviceStates(it) }
    return Observable.combineLatest(devices) {
        Timber.d("deviceStates combineLatest $it")
        it.map { it as Either<BeckonDeviceError, BeckonState<State>> }.toList()
    }
}

fun BeckonClientRx.deviceStates(address: MacAddress): Observable<Either<BeckonDeviceError, BeckonState<State>>> {
    return findSavedDeviceE(address)
        .flatMapObservableEither { findConnectedDeviceO(it) }
        .flatMapZ { it.deviceStates() }
}

fun BeckonClientRx.findSavedDeviceE(macAddress: MacAddress): Single<Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata>> {
    return findSavedDevice(macAddress).z { BeckonDeviceError.SavedDeviceNotFound(macAddress) }
}

fun BeckonClientRx.scanAndConnect(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<BeckonResult<BeckonDeviceRx>> {

    val searchStream =
        search(conditions, setting, descriptor).map { it.mapLeft { BeckonException(it) } }

    val scanStream = scan(conditions, setting)
        .flatMapSingleEither { safeConnect(it, descriptor) }

    return Observable.merge(scanStream, searchStream)
}

fun BeckonClientRx.safeConnect(
    result: ScanResult,
    descriptor: Descriptor
): Single<BeckonResult<BeckonDeviceRx>> {
    return connect(result, descriptor).map { it.right() as BeckonResult<BeckonDeviceRx> }
        .doOnSuccess { Timber.d("safe Connect $it") }
        .onErrorReturn { it.left() }
}

fun BeckonClientRx.scan(
    conditions: Observable<Boolean>,
    setting: ScannerSetting
): Observable<BeckonResult<ScanResult>> {
    return conditions
        .switchMap {
            if (it) {
                Timber.d("Scan conditions are meet, start scanning")
                startScan(setting)
            } else {
                Timber.d("Scan conditions are not meet, stop scanning")
                stopScan()
                Observable.empty()
            }
        }
        .distinct { it.macAddress }
        .z(::identity)
        .doOnNext { Timber.d("Scan found $it") }
        .doOnDispose {
            Timber.d("Scan stream is disposed, stop scanning")
            stopScan()
        }
}

fun BeckonClientRx.search(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<Either<ConnectionError, BeckonDeviceRx>> {
    return conditions
        .switchMap {
            if (it) {
                Timber.d("Scan conditions are meet, start searching")
                search(setting, descriptor)
            } else {
                Timber.d("Scan conditions are not meet, stop searching")
                Observable.empty()
            }
        }
        .doOnNext { Timber.d("Search found $it") }
}

fun BeckonClientRx.scanAndSave(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor,
    filter: (State) -> Boolean
): Observable<BeckonResult<MacAddress>> {
    return scanAndConnect(conditions.distinctUntilChanged(), setting, descriptor)
        .doOnNext { Timber.d("Scan And Connect: $it") }
        .flatMapZ { it.deviceStates() }
        // .doOnNext { Timber.d("Device state: $it") }
        .filterZ {
            deviceState ->
            Timber.d("Device state: ${deviceState.state}")
            filter(deviceState.state)
        }
        .flatMapSingleZ { save(it.metadata.macAddress) }
        .doOnNext { Timber.d("scanAndSave found $it") }
}

fun BeckonClientRx.connectSavedDevice(macAddress: MacAddress): Single<BeckonDeviceRx> {
    return findSavedDevice(macAddress).flatMap { connect(it) }
}
