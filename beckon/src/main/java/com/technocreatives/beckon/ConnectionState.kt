package com.technocreatives.beckon

sealed class ConnectionState {
    object NotStarted : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
    object Connecting : ConnectionState()
    class Failed(val error: Throwable) : ConnectionState()
}
