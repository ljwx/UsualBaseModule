package com.jdcr.usualbasemodule

import android.app.Dialog
import android.widget.Toast
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jdcr.basedialog.BaseModuleDialog
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseModuleDialogTest {

    private lateinit var activity: MainActivity

    fun dialog() {
        var dialog: Dialog? = null
        dialog = BaseModuleDialog.Builder(activity)
            .setView(com.jdcr.baseresource.R.layout.base_resource_dialog_example).setTitle("111")
            .setMessage("222").setPositiveButton("333").setNegativeButton("444", {
                dialog?.dismiss()
                Toast.makeText(activity, "取消了", Toast.LENGTH_SHORT).show()
            }).create()
        dialog.show()
    }

}