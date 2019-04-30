package com.axkid.helios

import android.app.Application
import android.content.Context
import com.technocreatives.beckon.BeckonClient

class App : Application() {

    private lateinit var beckonClient: BeckonClient

    companion object {
        operator fun get(context: Context) =
                context.applicationContext as App
    }

    fun beckonClient() = beckonClient

    override fun onCreate() {
        super.onCreate()
        beckonClient = BeckonClient.create(this)
    }

}
