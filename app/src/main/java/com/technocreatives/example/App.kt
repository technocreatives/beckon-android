package com.technocreatives.example

import android.app.Application
import android.content.Context
import com.technocreatives.beckon.rx2.BeckonClientRx
import timber.log.Timber

class App : Application() {

    private lateinit var beckonClient: com.technocreatives.beckon.rx2.BeckonClientRx

    companion object {
        operator fun get(context: Context) =
                context.applicationContext as App
    }

    fun beckonClient() = beckonClient

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        beckonClient = com.technocreatives.beckon.rx2.BeckonClientRx.create(this)
    }

}
