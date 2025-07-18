package com.jdcr.basecamera

import android.util.Log


object CameraLog {

    fun d(content: String) {
        Log.d("摄像头", content)
    }

    fun e(content: String) {
        Log.e("摄像头", content)
    }

}