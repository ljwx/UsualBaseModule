package com.jdcr.usualbasemodule

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.jdcr.basebase.loading.BaseCommonLoadingWindow
import com.jdcr.baseble.BluetoothDeviceManager
import com.jdcr.baseble.core.scan.BluetoothDeviceScanner
import com.jdcr.baseble.core.scan.BluetoothDeviceScanner.ScanDeviceResult
import com.jdcr.baseble.test.BluetoothDeviceTest
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseeventbus.FlowEventBus
import com.jdcr.basemultiplestate.BaseModuleMultipleState
import com.jdcr.network.ktor.KtorClientManager
import com.jdcr.selftestcompose.FirstComposeActivity
import com.jdcr.usualbasemodule.ui.theme.UsualBaseModuleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var test: BluetoothDeviceTest = BluetoothDeviceTest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            UsualBaseModuleTheme {
                val deviceList = remember { mutableStateListOf<ScanDeviceResult>() }

                var currentDevice = remember { mutableStateOf<ScanDeviceResult?>(null) }

                // 设置扫描结果监听器
                LaunchedEffect(Unit) {
//                    manager?.setScanResultListener { devices ->
//                        // 过滤掉名称为空的设备，并更新列表和映射
//                        val filteredDevices = devices.filter { it.name != null }
//                        deviceList.clear()
//
//                        filteredDevices.forEach { device ->
//                            deviceList.add(device)
//                        }
//                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Row {
                            Greeting(
                                name = "Android",
                                modifier = Modifier.padding(innerPadding)
                            )
                            Button(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@MainActivity,
                                            FirstComposeActivity::class.java
                                        )
                                    )
                                },
                                modifier = Modifier.padding(innerPadding),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("显示对话框")
                            }
                            Text(
                                text = currentDevice.value?.displayName() ?: "",
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        Row {
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    lifecycleScope.launch {
                                        test.startScan(arrayOf())?.collect { devices ->
                                            // 过滤掉名称为空的设备，并更新列表和映射
                                            val filteredDevices = devices.filter { it.name != null }
                                            deviceList.clear()

                                            filteredDevices.forEach { device ->
                                                deviceList.add(device)
                                            }
                                        }
                                    }
                                }) {
                                Text("开始扫描")
                            }
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    test.stopScan()
                                }) {
                                Text("停止扫描")
                            }
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    test.stopScan()
                                    currentDevice.value?.let {
                                        test.connect(it.address)
                                    }
                                }) {
                                Text("连接")
                            }
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    test.disconnect(currentDevice.value?.address)
                                }) {
                                Text("断开连接")
                            }
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    test.readTemperature(currentDevice.value?.address)
                                }) {
                                Text("读取温度")
                            }
                            Button(
                                modifier = Modifier.padding(0.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    test.writeTextToLed(currentDevice.value?.address)
                                }) {
                                Text("写入LED")
                            }
                        }

                        // 显示扫描到的设备列表
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            items(deviceList) { device ->
                                Row {
                                    Text(
                                        text = device.displayName(),
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                currentDevice.value = device
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        FlowEventBus.getInstance().subscribe("", lifecycleScope) {

        }
        FlowEventBus.getInstance().subscribe("", lifecycleScope) {

        }
        FlowEventBus.getInstance().subscribe("", lifecycleScope) {

        }
        lifecycleScope.launch {
            FlowEventBus.getInstance().post("eee")
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
    rememberCoroutineScope()
    Text(
        text = "Hello $name!",
        modifier = modifier,
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefreshScreen() {
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshScope.launch {
                refreshing = true
                delay(1500) // 模拟刷新
                refreshing = false
            }
        }
    )
    val items = remember { (1..20).map { "Item $it" } }
    val refreshTrigger = 0.8f // 下拉到这个比例触发刷新

    Column(Modifier.fillMaxSize()) {
        // 使用 graphicsLayer 来偏移 PullRefreshIndicator
        PullRefreshIndicator(
            refreshing,
            pullRefreshState,
            Modifier
                .align(Alignment.CenterHorizontally)
                .graphicsLayer {
                    // 根据下拉进度和是否正在刷新来决定 Y 轴的偏移
                    translationY = if (!refreshing) {
                        pullRefreshState.progress * 100.dp.toPx() // 调整 100.dp 控制跟随距离
                    } else {
                        0f
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items) { item ->
                    Text(item, Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun ThirdPartyPullToRefreshScreen() {
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = refreshing)
    val items = remember { (1..10).map { "Item $it" } }

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            refreshScope.launch {
                refreshing = true
                delay(1500)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items) { item ->
                Text(item, Modifier.padding(16.dp))
            }
        }
    }
}