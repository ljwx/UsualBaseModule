## 依赖
```kotlin
dependencies {
    // CameraX core libraries
    def camerax_version = "1.3.4" // 建议使用最新稳定版
    implementation "androidx.camera:camera-core:${camerax_version}"
    implementation "androidx.camera:camera-camera2:${camerax_version}"
    implementation "androidx.camera:camera-lifecycle:${camerax_version}"
    implementation "androidx.camera:camera-view:${camerax_version}"
}
```

## 权限
```kotlin
<uses-permission android:name="android.permission.CAMERA" />

<uses-feature android:name="android.hardware.camera.any" />

<uses-feature android:name="android.hardware.camera.any" />
<!-- 等同于 <uses-feature android:name="android.hardware.camera.any" android:required="true" /> -->
```
1. <uses-feature> 标签用于声明应用需要或支持的硬件 / 软件功能
2. 这个特性声明表示应用需要或支持任何类型的摄像头（前置、后置或外部摄像头）
3. 若设置 android:required="false"，应用可在无摄像头的设备上运行，但相关功能会被禁用

> 除了 camera.any，还有针对特定摄像头的特性声明：

1. android.hardware.camera
声明需要后置摄像头（不包括前置）。
2. android.hardware.camera.front
声明需要前置摄像头。
3. android.hardware.camera.autofocus
声明需要摄像头支持自动对焦功能。