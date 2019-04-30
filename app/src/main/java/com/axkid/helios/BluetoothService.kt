package com.axkid.helios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.DeviceChange
import com.technocreatives.beckon.disposedBy
import com.technocreatives.beckon.states
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber


class BluetoothService : Service() {

    private val beckon by lazy { App[this].beckonClient() }

    private val bag = CompositeDisposable()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")

        beckon.devices()
                .doOnNext { Timber.d("Connected devices $it") }
                .flatMapIterable { it }
                .doOnNext { Timber.d("All devices $it") }
                .map { it to beckon.findDevice(it) }
                .filter { it.second != null }
                .flatMap { pair -> pair.second!!.changes().map { DeviceChange(pair.first, it) } }
                .subscribe { Timber.d("All changes: $it") }
                .disposedBy(bag)

        // Start Service in foreground
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForeground() {

        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel("pansar_service", "Pansar Background Service")
                } else {
                    // If earlier version channel ID is not used
                    // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                    ""
                }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        startForeground(101, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun watchDevice(device: BeckonDevice) {
        val defaultState = AxkidState(SeatedState.Unseated, 22, 1L)
        device.states(mapper, reducer, defaultState)
                .subscribe {
                    Timber.d("new State $it")
                }.disposedBy(bag)

    }

    override fun onDestroy() {
        bag.clear()
        super.onDestroy()
    }
}