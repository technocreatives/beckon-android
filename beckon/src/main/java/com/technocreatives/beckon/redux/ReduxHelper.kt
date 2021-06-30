package com.technocreatives.beckon.redux

import com.lenguyenthanh.redux.core.Log
import com.lenguyenthanh.redux.flow.FlowStore
import com.technocreatives.beckon.BluetoothState
import timber.log.Timber

internal typealias BeckonStore = FlowStore<BeckonState, BeckonAction>

internal fun createBeckonStore(): BeckonStore {
    return FlowStore(
        reducer, { BeckonState(emptyList(), emptyList(), BluetoothState.UNKNOWN) },
        log = object : Log {
            override fun log(tag: String, message: String) {
                Timber.tag("BeckonRedux").d(message)
            }
        }
    )
}