package com.technocreatives.example.bond

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.technocreatives.example.bond.domain.LocationPermissionState
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

class PermissionsService(
    private val fragment: AppCompatActivity,
    private val permissions: List<String>,
    private val rationale: String,
    private val requestCode: Int,
    private val callbacks: PermissionsServiceCallbacks
) : PermissionsServiceCallbacks by callbacks{

    fun hasPermission(): Boolean {
        return EasyPermissions.hasPermissions(fragment!!, *permissions.toTypedArray())
    }

    fun requestPermissions() {
        EasyPermissions.requestPermissions(
            fragment, rationale,
            requestCode, *permissions.toTypedArray()
        )
    }
}

interface PermissionsServiceCallbacks : EasyPermissions.PermissionCallbacks {
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    fun onResume(isHasPermissions: Boolean)
}
interface CustomEasyPermission: EasyPermissions.PermissionCallbacks{
    fun initCallback(fragment: Fragment){

    }
}
class MagicCallbacks : CustomEasyPermission{

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        throw UnsupportedOperationException("not implemented")
    }
}


class LocationPermissionsCallbacks(private val fragment: AppCompatActivity, private val permissions: List<String>, private val appStore: AppStore) : PermissionsServiceCallbacks {

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(fragment, perms)) {
            Toast.makeText(fragment!!, "Show dialog", Toast.LENGTH_SHORT).show()
            updateLocationPermissionState(LocationPermissionState.DONT_ASK_AGAIN)
            val dialog = AppSettingsDialog.Builder(fragment).build()
            dialog.show()
        } else {
            updateLocationPermissionState(LocationPermissionState.DENIED)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        // todo I think it doesn't work correctly if we request multiple permissions
        updateLocationPermissionState(LocationPermissionState.ON)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE) {
            // Do something after user returned from app settings screen, like showing a Toast.
            if (EasyPermissions.hasPermissions(fragment!!, *permissions.toTypedArray())) {
                Toast.makeText(fragment!!, "Received permissions via settings", Toast.LENGTH_SHORT)
                    .show()
                updateLocationPermissionState(LocationPermissionState.ON)
            }
        }
    }

    override fun onResume(isHasPermissions: Boolean) {
       val state = if (isHasPermissions) {
            LocationPermissionState.ON
        } else {
            LocationPermissionState.UNKNOWN
        }
        updateLocationPermissionState(state)
    }

    private fun updateLocationPermissionState(state: LocationPermissionState) {
        appStore.dispatch(BondAction.UpdateLocationPermissionState(state))
    }
}
