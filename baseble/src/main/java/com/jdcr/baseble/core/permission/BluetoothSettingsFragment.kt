package com.jdcr.baseble.core.permission

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class BluetoothSettingsFragment : Fragment() {

    private var callback: ((enabled: Boolean) -> Unit)? = null

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        callback?.invoke(BluetoothAdapter.getDefaultAdapter()?.isEnabled == true)
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onStart() {
        super.onStart()
        launcher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    companion object {
        fun open(activity: FragmentActivity, callback: (enabled: Boolean) -> Unit) {
            val fragment = BluetoothSettingsFragment().apply { this.callback = callback }
            activity.supportFragmentManager
                .beginTransaction()
                .add(fragment, "BluetoothSettingsFragment")
                .commitNow()
        }
    }

}