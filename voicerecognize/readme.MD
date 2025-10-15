> 步骤 1: 添加 Gradle 依赖
打开您 app 模块的 build.gradle 文件 (通常是 app/build.gradle)，在 dependencies 代码块中添加 Vosk 的依赖：
implementation 'com.alphacephei:vosk-android:0.3.46'
> 注意: 0.3.46 是目前较新的稳定版本，您可以访问 Vosk's Maven Repository 查看最新版本号。
添加后，点击 Android Studio 顶部的 "Sync Now" 按钮同步项目。

步骤 2: 下载并集成中文模型
这是最关键的一步。离线识别需要的所有数据都在模型文件中。
前往 Vosk 模型下载页面:
https://alphacephei.com/vosk/models
选择并下载中文模型:
在页面上找到 "Chinese" 部分。您会看到多个不同大小的模型。对于移动端应用，强烈推荐使用轻量级模型，以控制应用体积。
推荐: vosk-model-small-cn-0.22 (约 45MB)。这个模型在体积和识别率之间取得了很好的平衡。
点击下载，您会得到一个 .zip 压缩包。
将模型集成到项目中:
解压下载的 .zip 文件。解压后，您会得到一个文件夹，例如 vosk-model-small-cn-0.22。
在 Android Studio 的 Project 视图下，找到 app/src/main/ 目录。
右键点击 main 目录 -> New -> Directory，创建一个名为 assets 的文件夹（如果它已存在，则跳过此步）。
右键点击 assets 文件夹 -> New -> Directory，创建一个名为 vosk-models 的文件夹（为了规范管理）。
将刚才解压得到的整个模型文件夹 (vosk-model-small-cn-0.22) 复制并粘贴到 app/src/main/assets/vosk-models/ 目录下。
完成后的目录结构应该如下所示：
app/
└── src/
└── main/
├── assets/
│   └── vosk-models/
│       └── vosk-model-small-cn-0.22/
│           ├── am/
│           ├── conf/
│           ├── graph/
│           └── ivector/
└── java/
└── res/

步骤 3: 添加权限和配置
在 AndroidManifest.xml 中添加录音权限:
（可选）在 build.gradle 中处理原生库打包:
为了避免一些设备上出现 UnsatisfiedLinkError，建议在 android 代码块中添加以下配置，确保 .so 库被正确打包。
// app/build.gradle
android {
// ...
packagingOptions {
jniLibs {
useLegacyPackaging = true
}
}
}

步骤 4: 编写核心代码
Vosk 的 Android API 设计得非常友好。核心是 SpeechService 和 RecognitionListener。
下面是一个完整的 MainActivity.kt 示例，包含了权限请求、模型加载和实时识别的所有逻辑。
首先，准备一个简单的布局 activity_main.xml:
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
android:layout_width="match_parent"
android:layout_height="match_parent"
android:orientation="vertical"
android:gravity="center"
android:padding="16dp">

    <TextView
        android:id="@+id/result_text"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:text="准备就绪..."
        android:textAppearance="@style/TextAppearance.AppCompat.Large" />

    <TextView
        android:id="@+id/partial_result_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="中间结果..."
        android:textColor="@android:color/darker_gray" />

    <Button
        android:id="@+id/toggle_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="开始识别" />

</LinearLayout>
然后，是 MainActivity.kt 的完整代码:
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class MainActivity : AppCompatActivity(), RecognitionListener {

    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    private var model: Model? = null
    private var speechService: SpeechService? = null

    private lateinit var resultTextView: TextView
    private lateinit var partialResultTextView: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.result_text)
        partialResultTextView = findViewById(R.id.partial_result_text)
        toggleButton = findViewById(R.id.toggle_button)

        toggleButton.setOnClickListener {
            if (speechService != null) {
                // 如果正在识别，则停止
                stopRecognition()
            } else {
                // 检查权限并开始识别
                checkPermissionAndStart()
            }
        }

        // 初始化模型
        initModel()
    }

    private fun initModel() {
        // 模型加载是一个耗时操作，Vosk 推荐在子线程进行
        // StorageService 会帮助我们把 assets 中的模型解压到应用内部存储
        // 第二个参数 "vosk-model-small-cn-0.22" 是 assets 中模型文件夹的名字
        StorageService.unpack(this, "vosk-models/vosk-model-small-cn-0.22", "model-cn",
            { model ->
                this.model = model
                // 模型加载成功后，更新UI
                runOnUiThread {
                    toggleButton.isEnabled = true
                    resultTextView.text = "模型加载成功，可以开始识别"
                }
            },
            { exception ->
                // 模型加载失败
                runOnUiThread {
                    setErrorState("模型加载失败: " + exception.message)
                }
            })
    }
    
    private fun checkPermissionAndStart() {
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        } else {
            startRecognition()
        }
    }

    private fun startRecognition() {
        if (model == null) {
            Toast.makeText(this, "模型未加载，请稍后...", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // 使用模型创建一个 Recognizer
            val rec = Recognizer(model, 16000.0f)
            // 初始化 SpeechService，它会处理麦克风录音
            speechService = SpeechService(rec, 16000.0f)
            // 设置监听器
            speechService?.startListening(this)
            // 更新UI
            toggleButton.text = "停止识别"
            resultTextView.text = "正在聆听..."
        } catch (e: IOException) {
            setErrorState("启动识别失败: " + e.message)
        }
    }

    private fun stopRecognition() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        toggleButton.text = "开始识别"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecognition()
            } else {
                Toast.makeText(this, "权限被拒绝，无法使用语音识别功能", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRecognition()
    }

    // --- RecognitionListener 回调方法 ---

    override fun onPartialResult(hypothesis: String) {
        // 部分识别结果 (实时)
        val partialJson = org.json.JSONObject(hypothesis)
        partialResultTextView.text = partialJson.getString("partial")
    }

    override fun onResult(hypothesis: String) {
        // 最终识别结果
        val resultJson = org.json.JSONObject(hypothesis)
        resultTextView.append("\n" + resultJson.getString("text"))
    }

    override fun onFinalResult(hypothesis: String) {
        // 所有语音输入结束后的最终结果
        val finalJson = org.json.JSONObject(hypothesis)
        resultTextView.append("\n" + finalJson.getString("text"))
        // 识别结束后，重置UI
        stopRecognition()
    }

    override fun onError(exception: Exception) {
        setErrorState("识别出错: " + exception.message)
    }

    override fun onTimeout() {
        // 超时
        stopRecognition()
    }

    private fun setErrorState(message: String) {
        resultTextView.text = message
        toggleButton.text = "开始识别"
    }
}

运行和测试
现在您可以运行您的应用了。
首次启动时，应用会比较慢，因为它需要将 assets 中的模型文件解压到手机内部存储。这只会发生一次。StorageService.unpack 会处理好这一切。
模型加载成功后，点击 "开始识别" 按钮，对着手机说话，您应该能看到实时的识别结果和最终结果。
这个教程涵盖了使用 Vosk 的所有核心步骤。您可以基于这个示例进行扩展，例如添加到您的 Service 中，或者设计更复杂的 UI。
