package com.jdcr.basekmplog

import platform.Foundation.NSLog

actual class BaseKmpLog private constructor() {
    actual companion object {
        actual fun v(tag: String, message: String) {
            NSLog("[V] $tag: $message")
        }

        actual fun d(tag: String, message: String) {
            NSLog("[D] $tag: $message")
        }

        actual fun i(tag: String, message: String) {
            NSLog("[I] $tag: $message")
        }

        actual fun w(tag: String, message: String) {
            NSLog("[W] $tag: $message")
        }

        actual fun e(tag: String, message: String) {
            NSLog("[E] $tag: $message")
        }

        actual fun e(tag: String, message: String, throwable: Throwable) {
            NSLog("[E] $tag: $message\n${throwable.stackTraceToString()}")
        }

        actual fun iNetwork(message: String, throwable: Throwable?) {
            NSLog("[I] BaseNetwork: $message,${throwable?.stackTraceToString()}")
        }

        actual fun eEventBus(message: String, throwable: Throwable?) {
            NSLog("[I] BaseEventBus: $message,${throwable?.stackTraceToString()}")
        }

    }
}