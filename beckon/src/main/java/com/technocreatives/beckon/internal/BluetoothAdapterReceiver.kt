package com.technocreatives.beckon.internal

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lenguyenthanh.redux.core.Dispatcher
import com.technocreatives.beckon.BluetoothState
import com.technocreatives.beckon.redux.BeckonAction
import kotlinx.coroutines.runBlocking

internal interface Receiver {
    fun register(context: Context)
    fun unregister(context: Context)
}

internal class BluetoothAdapterReceiver(private val dispatcher: Dispatcher<BeckonAction>) :
    BroadcastReceiver(), Receiver {

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            runBlocking {
                dispatcher.dispatch(BeckonAction.ChangeBluetoothState(state.toBluetoothState()))
            }
        }
    }

    override fun register(context: Context) {
        context.registerReceiver(
            this,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        // Post initial state
        runBlocking {
            dispatcher.dispatch(BeckonAction.ChangeBluetoothState(BluetoothAdapter.getDefaultAdapter().state.toBluetoothState()))
        }
    }

    override fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }
}

private fun Int.toBluetoothState(): BluetoothState {
    return when (this) {
        BluetoothAdapter.STATE_ON -> BluetoothState.ON
        BluetoothAdapter.STATE_OFF -> BluetoothState.OFF
        BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TURNING_ON
        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TURNING_OFF
        else -> BluetoothState.UNKNOWN
    }
}
