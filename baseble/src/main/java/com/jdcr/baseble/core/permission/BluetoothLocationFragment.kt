package com.jdcr.baseble.core.permission

import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils

class BluetoothLocationFragment : Fragment() {

    private var callback: ((enabled: Boolean) -> Unit)? = null

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        callback?.invoke(BluetoothDeviceUtils.isLocationEnable(requireContext()))
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onStart() {
        super.onStart()
        BleLog.i("请求打开定位")
        launcher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    companion object {
        fun open(activity: FragmentActivity, callback: (enabled: Boolean) -> Unit) {
            val fragment = BluetoothLocationFragment().apply { this.callback = callback }
            activity.supportFragmentManager
                .beginTransaction()
                .add(fragment, "LocationSettingsFragment")
                .commitNow()
        }
    }

}