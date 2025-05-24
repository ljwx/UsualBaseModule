package com.jdcr.usualbasemodule

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.jdcr.basebase.loading.BaseCommonLoadingWindow
import com.jdcr.basemultiplestate.BaseModuleMultipleState
import com.jdcr.usualbasemodule.ui.theme.UsualBaseModuleTheme

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsualBaseModuleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(onClick = {
                            execute()
                        }) {
                            Text("显示对话框")
                        }
                        CustomView()
                    }
                }
            }
        }
    }

    val dialog by lazy { BaseCommonLoadingWindow(this) }

    fun execute() {
        if (dialog.isShowing()) {
            dialog.dismiss()
        } else {
            dialog.show(false, false)
        }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UsualBaseModuleTheme {
        Row {
            Greeting("Android")
        }
    }
}

@Composable
fun CustomView() {
    AndroidView(factory = { context ->
        BaseModuleMultipleState(context).apply {
            addContentView(TextView(context).apply { text = "内容" })
            addEmptyView(TextView(context).apply { text = "空空如也" })
            addErrorView(TextView(context).apply { text = "出错了" })
            showEmpty()
        }
    })
}