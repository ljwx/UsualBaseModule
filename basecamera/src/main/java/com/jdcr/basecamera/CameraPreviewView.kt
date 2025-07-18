package com.jdcr.basecamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import com.codemao.toolssdk.R
import com.codemao.toolssdk.model.dsbridge.camera.JSICameraStartControl
import com.codemao.toolssdk.utils.ExtLog
import com.codemao.toolssdk.view.MaskOverlayView
import androidx.core.graphics.toColorInt

class CameraPreviewView(
    private val context: Context,
    private val option: JSICameraStartControl,
    private var closeListener: View.OnClickListener?
) :
    FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val cornerRadiusPx = (option.radius ?: 20f) * density

    init {
        id = R.id.toolsdk_camera_preview_layout
        setupOverlayView()
    }

    override fun dispatchDraw(canvas: Canvas) {
        try {
            val path = Path()
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val radii = floatArrayOf(
                cornerRadiusPx, cornerRadiusPx,
                cornerRadiusPx, cornerRadiusPx,
                0f, 0f,
                0f, 0f
            )
            path.addRoundRect(rect, radii, Path.Direction.CW)
            canvas.clipPath(path)
            super.dispatchDraw(canvas)

            drawMaskAndBorder(canvas)
        } catch (e: Exception) {
            ExtLog.dCamera("绘制圆角失败：${e.message}")
            super.dispatchDraw(canvas)
        }
    }

    /**
     * 绘制半透明遮罩和虚线边框
     */
    private fun drawMaskAndBorder(canvas: Canvas) {
        try {
            // 获取虚线边框的位置和尺寸
            val borderRect = option.getOverlayRectF(context)
            val strokeRect = option.getOverlayRectFStroke(context)
            val cornerRadius = option.getStrokeRadius(context).toFloat()

            val layerId = canvas.saveLayer(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                null,
                Canvas.ALL_SAVE_FLAG
            )

            // 1. 先绘制整个区域的半透明遮罩
            val maskPaint = Paint().apply {
                color = "#80000000".toColorInt() // 半透明黑色
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

            // 2. 在虚线框区域"挖掉"遮罩，使其完全透明
            val clearPaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            if (cornerRadius > 0) {
                canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, clearPaint)
            } else {
                canvas.drawRect(borderRect, clearPaint)
            }
            canvas.restoreToCount(layerId)

            // 3. 最后绘制虚线边框
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f // 4px宽度
                color = Color.WHITE // 白色虚线
                pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f) // 虚线模式
            }

            if (cornerRadius > 0) {
                canvas.drawRoundRect(strokeRect, cornerRadius, cornerRadius, borderPaint)
            } else {
                canvas.drawRect(strokeRect, borderPaint)
            }

            ExtLog.dCamera("绘制半透明遮罩和虚线边框: ${borderRect.left},${borderRect.top},${borderRect.right},${borderRect.bottom}")
        } catch (e: Exception) {
            ExtLog.dCamera("绘制半透明遮罩和虚线边框失败：${e.message}")
        }
    }

    private fun setupOverlayView() {
        LayoutInflater.from(context)
            .inflate(R.layout.toolsdk_camera_preview_overlay, this, true)
            .apply {
                findViewById<View>(R.id.camera_close).apply {
                    setOnClickListener(closeListener)
                    (layoutParams as? LayoutParams)?.setMargins(
                        0,
                        option.dp2px(context, option.closeTop ?: 12f), // 上边距，默认12dp
                        option.dp2px(context, option.closeRight ?: 12f), // 右边距，默认12dp
                        0
                    )
                }
                findViewById<MaskOverlayView>(R.id.overlay_view)?.apply {
                    // MaskOverlayView完全透明，不遮挡SurfaceView
                    visibility = View.VISIBLE
                    maskColor = Color.TRANSPARENT
                    post {
                        val fullRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                        setTransparentRect(fullRect)
                    }
                    setDashedBorder(false) // 虚线边框和半透明遮罩都在CameraPreviewView中绘制
                }
                findViewById<View>(R.id.tips).apply {
                    (layoutParams as? LayoutParams)?.setMargins(
                        0,
                        0,
                        0,
                        option.dp2px(this.context, option.textBottom ?: 0f)
                    )
                }
            }
    }

    fun getPreviewView(): PreviewView {
        return findViewById(R.id.preview_view)
    }

    fun setCloseListener(listener: OnClickListener?) {
        this.closeListener = listener
    }

}