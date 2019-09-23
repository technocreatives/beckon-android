package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.Log
import com.lenguyenthanh.redux.Store
import com.technocreatives.beckon.BluetoothState
import timber.log.Timber

internal typealias BeckonStore = Store<BeckonState, BeckonAction>

internal fun createBeckonStore(): BeckonStore {
    return Store(reducer, { BeckonState(emptyList(), emptyList(), BluetoothState.UNKNOWN) }, log = object : Log {
        override fun log(tag: String, message: String) {
            Timber.tag("BeckonRedux").d(message)
        }
    })
}
