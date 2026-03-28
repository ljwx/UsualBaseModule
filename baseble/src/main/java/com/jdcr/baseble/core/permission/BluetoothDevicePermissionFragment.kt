package com.jdcr.baseble.core.permission

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class BluetoothDevicePermissionFragment : Fragment() {

    companion object {
        fun requestPermission(
            activity: FragmentActivity,
            permissions: Array<String>,
            callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
        ) {
            val fragment = BluetoothDevicePermissionFragment().apply {
                this.callback = callback
                this.permissions = permissions
            }
            activity.supportFragmentManager
                .beginTransaction()
                .add(fragment, "BluetoothDevicePermissionFragment")
                .commitNow()
        }
    }

    private var permissions: Array<String>? = null

    private var callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)? = null

    private val launcher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            callback?.invoke(result.values.all { it }, result)
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }

    override fun onStart() {
        super.onStart()
        permissions?.let { launcher.launch(it) }
    }

}