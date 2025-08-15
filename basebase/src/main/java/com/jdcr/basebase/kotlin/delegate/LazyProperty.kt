package com.jdcr.basebase.kotlin.delegate

import kotlin.reflect.KProperty

class LazyProperty<T>(private val initializer: () -> T) {

    private val lazyValue: Lazy<T> = lazy(initializer)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return lazyValue.value
    }

    fun isInitialized(): Boolean {
        return lazyValue.isInitialized()
    }

}