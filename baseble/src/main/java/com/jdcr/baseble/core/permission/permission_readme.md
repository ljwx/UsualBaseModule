|Android 版本	|蓝牙核心权限	|扫描设备额外要求	| 权限类型 |
|-|-|-|-|
|≤ Android 5	|BLUETOOTH、BLUETOOTH_ADMIN	|无需定位权限	|普通权限（无需动态申请）
|Android 6-9	|BLUETOOTH、BLUETOOTH_ADMIN	|需 ACCESS_COARSE/FINE_LOCATION（动态申请）	|危险权限
|Android 10	|BLUETOOTH、BLUETOOTH_ADMIN	|需 ACCESS_FINE_LOCATION（动态申请）+ 后台定位（可选）	|危险权限
|Android 11	|BLUETOOTH、BLUETOOTH_ADMIN	|需 ACCESS_FINE_LOCATION（动态申请）	|危险权限
|Android 12+	|BLUETOOTH_SCAN、BLUETOOTH_CONNECT	|无需定位权限（可通过 neverForLocation 标记）	|危险权限
|Android 13+	|新增 BLUETOOTH_ADVERTISE（广播用）	|同 Android 12+	|危险权限


1. AndroidManifest.xml（版本适配版）
   添加tools:targetApi区分不同版本权限，避免低版本编译报错：
```kotlin
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.example.bluetoothdemo">

    <!-- 基础蓝牙权限（所有版本） -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

    <!-- Android 6-11：扫描蓝牙需要定位权限 -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="28" /> <!-- 28=Android 9 -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" /> <!-- 30=Android 11 -->

    <!-- Android 12+：拆分后的蓝牙权限 -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:minSdkVersion="31"
        android:usesPermissionFlags="neverForLocation" /> <!-- 标记无需定位 -->
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"
        android:minSdkVersion="31" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE"
        android:minSdkVersion="33" /> <!-- Android 13+ 新增 -->

    <!-- 声明蓝牙硬件依赖 -->
    <uses-feature
        android:name="android.hardware.bluetooth"
        android:required="true" />
    <!-- 可选：如果只支持BLE蓝牙，添加这个 -->
    <!-- <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" /> -->

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BluetoothDemo"
        tools:targetApi="34">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```