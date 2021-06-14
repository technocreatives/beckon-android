package com.technocreatives.beckon.extension

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
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
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

fun BeckonClient.devicesStates(addresses: List<String>): Observable<List<Either<BeckonDeviceError, BeckonState<State>>>> {
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

fun BeckonClient.deviceStates(address: MacAddress): Observable<Either<BeckonDeviceError, BeckonState<State>>> {
    return findSavedDeviceE(address)
        .flatMapObservableEither { findConnectedDeviceO(it) }
        .flatMapZ { it.deviceStates() }
}

fun BeckonClient.findSavedDeviceE(macAddress: MacAddress): Single<Either<BeckonDeviceError.SavedDeviceNotFound, SavedMetadata>> {
    return findSavedDevice(macAddress).z { BeckonDeviceError.SavedDeviceNotFound(macAddress) }
}

fun BeckonClient.scanAndConnect(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<BeckonResult<BeckonDevice>> {

    val searchStream =
        search(conditions, setting, descriptor).map { it.mapLeft { BeckonException(it) } }
    val scanStream = scan(conditions, setting)
        .flatMapSingleEither { safeConnect(it, descriptor) }

    return Observable.merge(scanStream, searchStream)
}

fun BeckonClient.safeConnect(
    result: ScanResult,
    descriptor: Descriptor
): Single<BeckonResult<BeckonDevice>> {
    return connect(result, descriptor).map { it.right() as BeckonResult<BeckonDevice> }
        .onErrorReturn { it.left() }
}

fun BeckonClient.scan(
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

fun BeckonClient.search(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<Either<ConnectionError, BeckonDevice>> {
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

fun BeckonClient.scanAndSave(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor,
    filter: (State) -> Boolean
): Observable<BeckonResult<MacAddress>> {
    return scanAndConnect(conditions.distinctUntilChanged(), setting, descriptor)
        .flatMapZ { it.deviceStates() }
        .filterZ { deviceState -> filter(deviceState.state) }
        .flatMapSingleZ { save(it.metadata.macAddress) }
        .doOnNext { Timber.d("scanAndSave found $it") }
}

fun BeckonClient.connectSavedDevice(macAddress: MacAddress): Single<BeckonDevice> {
    return findSavedDevice(macAddress).flatMap { connect(it) }
}
