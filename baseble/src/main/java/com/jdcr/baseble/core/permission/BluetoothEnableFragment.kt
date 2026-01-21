package com.jdcr.baseble.core.permission

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

class BluetoothEnableFragment : Fragment() {

    private var callback: ((enabled: Boolean) -> Unit)? = null

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val enabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        callback?.invoke(enabled)
        parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
    }

    override fun onStart() {
        super.onStart()
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    companion object {
        fun request(activity: FragmentActivity, callback: (enabled: Boolean) -> Unit) {
            val fragment = BluetoothEnableFragment().apply { this.callback = callback }
            activity.supportFragmentManager
                .beginTransaction()
                .add(fragment, "BluetoothEnableFragment")
                .commitNow()
        }
    }
}