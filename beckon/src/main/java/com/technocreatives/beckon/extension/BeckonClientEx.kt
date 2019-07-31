package com.technocreatives.beckon.extension

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.left
import arrow.core.right
import com.technocreatives.beckon.BeckonClient
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.BeckonResult
import com.technocreatives.beckon.CharacteristicMapper
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.MacAddress
import com.technocreatives.beckon.ScanResult
import com.technocreatives.beckon.ScannerSetting
import com.technocreatives.beckon.WritableDeviceMetadata
import com.technocreatives.beckon.util.concatMapSingleEither
import com.technocreatives.beckon.util.filterEither
import com.technocreatives.beckon.util.flatMapEither
import com.technocreatives.beckon.util.flatMapSingleEither
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import timber.log.Timber

fun <Change, State> BeckonClient.deviceStates(
    addresses: List<String>,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<List<BeckonDeviceState>> {
    val devices = addresses.map { deviceStates(it, mapper, reducer, defaultState) }

    Timber.d("deviceStates $devices")
    return Observable.combineLatest(devices) {
        Timber.d("combineLatest ${it[0]}")
        it.map { it as BeckonDeviceState }.toList()
    }
}

fun <Change, State> BeckonClient.deviceStates(
    address: MacAddress,
    mapper: CharacteristicMapper<Change>,
    reducer: BiFunction<State, Change, State>,
    defaultState: State
): Observable<BeckonDeviceState> {
    return findSavedDeviceO(address)
        .flatMapObservable { metadata ->
            when (metadata) {
                is Some -> findConnectedDeviceO(metadata.t.macAddress).flatMapObservable {
                    when (it) {
                        is Some -> it.t.deviceStates(mapper, reducer, defaultState)
                        is None -> Observable.just(NoBeckonDeviceFound(metadata.t))
                    }
                }
                is None -> Observable.just(NoSavedDeviceFound(address))
            }
        }
}

fun BeckonClient.findSavedDeviceO(macAddress: MacAddress): Single<Option<WritableDeviceMetadata>> {
    return findSavedDevice(macAddress).map { Option.just(it) }
        .onErrorReturn { Option.empty() }
}

fun BeckonClient.findConnectedDeviceO(macAddress: MacAddress): Single<Option<BeckonDevice>> {
    return findConnectedDevice(macAddress).map { Option.just(it) }
        .onErrorReturn { Option.empty() }
}

// onError can be happen
fun BeckonClient.scanAndConnect(
    conditions: Observable<Boolean>,
    setting: ScannerSetting,
    descriptor: Descriptor
): Observable<BeckonResult<BeckonDevice>> {
    return scan(conditions, setting)
        .concatMapSingleEither { connect(it, descriptor) }
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
        .flatMapEither { it.deviceStates(mapper, reducer, defaultState) }
        .doOnNext { Timber.d("Scan device state $it") }
        .filterEither { deviceState -> filter(deviceState.state) }
        .flatMapSingleEither { save(it.metadata.macAddress) }
        .doOnNext { Timber.d("scanAndSave $it") }
}
