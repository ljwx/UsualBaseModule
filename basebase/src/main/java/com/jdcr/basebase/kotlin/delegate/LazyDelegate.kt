package com.jdcr.basebase.kotlin.delegate

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LazyDelegate<T>(private val initializer: () -> T) : ReadOnlyProperty<Any?, T>,
    InitializedCheck {

    private val lazyValue = lazy(initializer)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return lazyValue.value
    }

    override fun isInitialized(): Boolean {
        return lazyValue.isInitialized()
    }

}

fun <T> lazyDelegate(initializer:()->T):LazyDelegate<T> {
    return LazyDelegate(initializer)
}