package com.technocreatives.example.bond.domain

import com.technocreatives.example.bond.AppStore
import io.reactivex.Observable

enum class LocationPermissionState {
    UNKNOWN,
    ON,
    DENIED,
    DONT_ASK_AGAIN
}
class LocationPermissionStateUseCase(private val appStore: AppStore) {
    operator fun invoke(): Observable<LocationPermissionState> {
        return appStore.states().map { it.locationPermissionState }
    }
}
