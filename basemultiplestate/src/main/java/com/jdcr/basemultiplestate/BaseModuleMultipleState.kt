package com.jdcr.basemultiplestate

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ViewFlipper
import androidx.annotation.LayoutRes
import com.jdcr.basebase.BaseModuleLog
import com.jdcr.basedefine.multiplestate.IViewStateLayout
import com.jdcr.basedefine.multiplestate.MultipleStateType

class BaseModuleMultipleState @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), IViewStateLayout {

    companion object {

        private var loadingLayoutDefault: Int = NO_ID
        private var emptyLayoutDefault: Int = NO_ID
        private var errorLayoutDefault: Int = NO_ID
        private var offlineLayoutDefault: Int = NO_ID
        private var extendLayoutDefault: Int = NO_ID

        private var inAnimationDefault: Animation? = null
        private var outAnimationDefault: Animation? = null

        fun setGlobalStateLayout(
            @MultipleStateType.MultipleStateType state: String,
            @LayoutRes layout: Int
        ) {
            when (state) {
                MultipleStateType.LOADING -> loadingLayoutDefault = layout
                MultipleStateType.EMPTY -> emptyLayoutDefault = layout
                MultipleStateType.ERROR -> errorLayoutDefault = layout
                MultipleStateType.OFFLINE -> offlineLayoutDefault = layout
                MultipleStateType.EXTEND -> extendLayoutDefault = layout
            }
        }

        fun setGlobalAnimation(inAnimation: Animation, outAnimation: Animation) {
            inAnimationDefault = inAnimation
            outAnimationDefault = outAnimation
        }

    }

    private var flipperView: ViewFlipper? = null

    @LayoutRes
    private var loadingLayout: Int = NO_ID
        get() = if (field == NO_ID) loadingLayoutDefault else field
        set(value) {
            if (field != value) {
                field = value
            }
        }

    @LayoutRes
    private var emptyLayout: Int = NO_ID
        get() = if (field == NO_ID) emptyLayoutDefault else field
        set(value) {
            if (field != value) {
                field = value
            }
        }

    @LayoutRes
    private var errorLayout: Int = NO_ID
        get() = if (field == NO_ID) errorLayoutDefault else field
        set(value) {
            if (field != value) {
                field = value
            }
        }

    @LayoutRes
    private var offlineLayout: Int = NO_ID
        get() = if (field == NO_ID) offlineLayoutDefault else field
        set(value) {
            if (field != value) {
                field = value
            }
        }

    @LayoutRes
    private var extendLayout: Int = NO_ID
        get() = if (field == NO_ID) extendLayoutDefault else field
        set(value) {
            if (field != value) {
                field = value
            }
        }

    private var inAnimation: Animation? = null
        get() = if (field == null) inAnimationDefault else field

    private var outAnimation: Animation? = null
        get() = if (field == null) outAnimationDefault else field

    private var viewIndex = -1
    private var loadingIndex: Int? = null
    private var contentIndex: Int? = null
    private var emptyIndex: Int? = null
    private var errorIndex: Int? = null
    private var offlineIndex: Int? = null
    private var extensionIndex: Int? = null

    init {
        if (layoutParams == null) {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        addViewFlipper()

        val attributes = context.obtainStyledAttributes(attrs, R.styleable.BaseModuleMultipleState)
        try {
            emptyLayout = attributes.getResourceId(
                R.styleable.BaseModuleMultipleState_stateLayoutEmpty,
                NO_ID
            )
            errorLayout = attributes.getResourceId(
                R.styleable.BaseModuleMultipleState_stateLayoutError,
                NO_ID
            )
            loadingLayout =
                attributes.getResourceId(
                    R.styleable.BaseModuleMultipleState_stateLayoutLoading,
                    NO_ID
                )
            offlineLayout =
                attributes.getResourceId(
                    R.styleable.BaseModuleMultipleState_stateLayoutOffline,
                    NO_ID
                )
            extendLayout =
                attributes.getResourceId(
                    R.styleable.BaseModuleMultipleState_stateLayoutExtend,
                    NO_ID
                )
            getLayoutStateView()
        } finally {
            attributes.recycle()
        }

        if (id == NO_ID) {
            id = com.jdcr.basedefine.R.id.base_module_multiple_state_layout_default
        }

        showContent()

    }

    private fun addViewFlipper() {
        if (flipperView == null) {
            val viewFlipper = ViewFlipper(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            flipperView = viewFlipper
        }
        if (flipperView?.parent == null) {
            addView(flipperView)
        }
    }

    private fun getLayoutStateView() {
        val contentView = getChildAt(0)
        if (contentView != null && contentView !is ViewFlipper) {
            removeView(contentView)
            addStateView(MultipleStateType.CONTENT, contentView)
        }
        if (loadingLayout != NO_ID) {
            val view = View.inflate(context, loadingLayout, null)
            addStateView(MultipleStateType.LOADING, view)
        }
        if (emptyLayout != NO_ID) {
            val view = View.inflate(context, emptyLayout, null)
            addStateView(MultipleStateType.EMPTY, view)
        }
        if (errorLayout != NO_ID) {
            val view = View.inflate(context, errorLayout, null)
            addStateView(MultipleStateType.ERROR, view)
        }
        if (offlineLayout != NO_ID) {
            val view = View.inflate(context, offlineLayout, null)
            addStateView(MultipleStateType.OFFLINE, view)
        }
        if (extendLayout != NO_ID) {
            val view = View.inflate(context, extendLayout, null)
            addStateView(MultipleStateType.EXTEND, view)
        }
    }

    private fun addStateView(@MultipleStateType.MultipleStateType state: String, view: View) {
        BaseModuleLog.dMultipleState("为" + state + "添加视图")
        flipperView?.addView(view)
        viewIndex += 1
        when (state) {
            MultipleStateType.LOADING -> loadingIndex = viewIndex
            MultipleStateType.CONTENT -> contentIndex = viewIndex
            MultipleStateType.EMPTY -> emptyIndex = viewIndex
            MultipleStateType.ERROR -> errorIndex = viewIndex
            MultipleStateType.OFFLINE -> offlineIndex = viewIndex
            MultipleStateType.EXTEND -> extensionIndex = viewIndex
        }
    }

    private fun showState(@MultipleStateType.MultipleStateType state: String) {
        BaseModuleLog.dMultipleState("显示" + state + "视图")
        var index: Int? = null
        when (state) {
            MultipleStateType.LOADING -> loadingIndex?.let { index = it }
            MultipleStateType.CONTENT -> contentIndex?.let { index = it }
            MultipleStateType.EMPTY -> emptyIndex?.let { index = it }
            MultipleStateType.ERROR -> errorIndex?.let { index = it }
            MultipleStateType.OFFLINE -> offlineIndex?.let { index = it }
            MultipleStateType.EXTEND -> extensionIndex?.let { index = it }
        }
        flipperView?.inAnimation = inAnimation
        flipperView?.outAnimation = outAnimation
        index?.let { flipperView?.displayedChild = it }
    }

    override fun addLoadingView(view: View) {
        addStateView(MultipleStateType.LOADING, view)
    }

    override fun addContentView(view: View) {
        addStateView(MultipleStateType.CONTENT, view)
    }

    override fun addEmptyView(view: View) {
        addStateView(MultipleStateType.EMPTY, view)
    }

    override fun addErrorView(view: View) {
        addStateView(MultipleStateType.ERROR, view)
    }

    override fun addOfflineView(view: View) {
        addStateView(MultipleStateType.OFFLINE, view)
    }

    override fun addExtensionView(view: View) {
        addStateView(MultipleStateType.EXTEND, view)
    }

    override fun showLoading() {
        showState(MultipleStateType.LOADING)
    }

    override fun showContent() {
        showState(MultipleStateType.CONTENT)
    }

    override fun showEmpty() {
        showState(MultipleStateType.EMPTY)
    }

    override fun showError() {
        showState(MultipleStateType.ERROR)
    }

    override fun showOffline() {
        showState(MultipleStateType.OFFLINE)
    }

    override fun showExtension() {
        showState(MultipleStateType.EXTEND)
    }

    override fun setInAnimation(animation: Animation) {
        this.inAnimation = animation
    }

    override fun setOutAnimation(animation: Animation) {
        this.outAnimation = animation
    }

}