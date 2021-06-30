package com.technocreatives.example.bond

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import arrow.core.Either
import com.technocreatives.beckon.Characteristic
import com.technocreatives.beckon.Descriptor
import com.technocreatives.beckon.Requirement
import com.technocreatives.beckon.rx2.devicesStates
import com.technocreatives.example.R
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

class BondActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private val component by lazy {
        BondComponent(this)
    }

    private var bag: CompositeDisposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        startBondService()
    }

    override fun onResume() {
        super.onResume()
        bag = CompositeDisposable()
        bag?.add(scan().subscribe { Timber.d("Scan result $it") })

        component.permissionsService.onResume(component.permissionsService.hasPermission())

        component.beckonClient.let { client ->
            bag?.add(client.savedDevices()
                    .map { it.map { it.macAddress } }
                    .doOnNext { Timber.d("All saved devices $it") }
                    .switchMap { client.devicesStates(it) }
                    .subscribe({
                        Timber.d("State of the universe $it")
                    }, {
                        Timber.e(it, "Error of the universe ")
                    }, {
                        Timber.d("The universe is dead")
                    }))
        }
    }

    override fun onPause() {
        bag?.let {
            if (!it.isDisposed) {
                it.clear()
            }
        }
        super.onPause()
    }

    private fun startBondService() {
        val intent = Intent(this, BondService::class.java)

        // Check version and start in foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun scan(): Observable<Either<Throwable, String>> {

        val requirements = listOf<Requirement>(
        )
        val subscribeList = listOf<Characteristic>(
        )
        val descriptor = Descriptor(requirements, subscribeList)

        return component.scanUseCase(component.scanSettings, descriptor)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        component.permissionsService.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        component.permissionsService.onPermissionsDenied(requestCode, perms)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        component.permissionsService.onPermissionsGranted(requestCode, perms)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        component.permissionsService.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
