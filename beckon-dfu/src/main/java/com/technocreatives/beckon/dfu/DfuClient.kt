package com.technocreatives.beckon.dfu

import android.content.Context
import androidx.annotation.RawRes
import arrow.core.Either
import com.technocreatives.beckon.dfu.internal.DfuClientImpl
import no.nordicsemi.android.dfu.DfuBaseService
import android.net.Uri as AndroidNetUri

interface DfuClient {
    fun start(conf: DfuConfiguration): Either<StartDfuError, DfuProcess>

    companion object {
        private var client: DfuClient? = null

        fun create(context: Context, dfuService: Class<out DfuBaseService>): DfuClient {
            if (client != null) {
                return client!!
            }

            client = DfuClientImpl(context, dfuService)
            return client!!
        }
    }
}

sealed interface StartDfuError {
    object AlreadyInProgress : StartDfuError
}

data class DfuConfiguration(
    val macAddress: String,
    val firmware: Firmware,
    val name: String? = null,
    val keepBond: Boolean = true,
    val foreground: Boolean = true,
    val unsafeExperimentalButtonlessServiceInSecureDfuEnabled: Boolean = true,
    val customNotifications: Boolean = false,
    val numberOfRetries: Int = 0
)

sealed interface Firmware {
    data class Uri(val uri: AndroidNetUri) : Firmware
    data class Path(val path: String) : Firmware
    data class AndroidRes(@RawRes val resource: Int) : Firmware

}