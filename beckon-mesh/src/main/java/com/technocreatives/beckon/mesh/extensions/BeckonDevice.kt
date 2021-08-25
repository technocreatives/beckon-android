package com.technocreatives.beckon.mesh.extensions

import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.ConnectionState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

suspend fun BeckonDevice.onDisconnect(f: suspend () -> Unit) {
    connectionStates().filter { it == ConnectionState.NotConnected }
        .first()
    f()
}