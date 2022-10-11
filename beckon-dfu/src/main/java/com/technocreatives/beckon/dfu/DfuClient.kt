package com.technocreatives.beckon.dfu

import android.content.Context
import arrow.core.Either
import com.technocreatives.beckon.dfu.internal.DfuClientImpl
import no.nordicsemi.android.dfu.DfuBaseService

interface DfuClient {
    fun start(conf: DfuConfiguration): Either<StartDfuError, DfuProcess>

    companion object {
        private var client: DfuClient? = null

        fun create(context: Context): DfuClient {
            if (client != null) {
                return client!!
            }

            client = DfuClientImpl(context)
            return client!!
        }
    }
}

sealed interface StartDfuError {
    object AlreadyInProgress : StartDfuError
}

data class DfuConfiguration(
    val macAddress: String,
    val firmware: Int, // Todo change to Uri
    val dfuService: Class<out DfuBaseService>,
    val name: String? = null,
    val keepBond: Boolean = true,
    val unsafeExperimentalButtonlessServiceInSecureDfuEnabled: Boolean = true,
    val customNotifications: Boolean = false,
    val numberOfRetries: Int = 0
)
