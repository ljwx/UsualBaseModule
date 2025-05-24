package com.jdcr.basebase

import android.util.Log

object BaseModuleLog {

    private val prefix = "tbm_"
    private val moduleApp = "app_"
    private val modulePage = "page_"
    private val moduleActivity = "actvy_"
    private val moduleFragment = "frgmt_"
    private val moduleToolbar = "tolba_"
    private val moduleRecycler = "recyc_"
    private val moduleScaffold = "scafd_"

    private val pageRun = prefix + modulePage + "run"
    private val activityRun = prefix + moduleActivity + "run"
    private val activityStart = prefix + moduleActivity + "start"
    private val fragmentRun = prefix + moduleFragment + "run"

    private val keyboardMonitor = prefix + "keyboardMonitor"
    private val toolbar = prefix + "toolbar"
    private val dialog = prefix + "dialog"
    private val event = prefix + "event"
    private val stateRefresh = prefix + "stateRefresh"
    private val viewmodel = prefix + "viewmodel"
    private val repository = prefix + "repository"
    private val permission = prefix + "permission"
    private val loading = prefix + "loading"
    private val multipleState = prefix + "multipleState"

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    fun dPageRun(content: String, className: String? = null) {
        Log.d(pageRun, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dActivity(content: String, className: String? = null) {
        Log.d(activityRun, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dActivityStart(content: String, className: String? = null) {
        Log.d(activityStart, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dToolbar(content: String, className: String? = null) {
        Log.d(toolbar, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dFragment(content: String, className: String? = null) {
        Log.d(fragmentRun, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dKeyboard(content: String, className: String? = null) {
        Log.d(keyboardMonitor, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dDialog(content: String, className: String? = null) {
        Log.d(dialog, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dEvent(content: String, className: String? = null) {
        Log.d(event, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dStateRefresh(content: String, className: String? = null) {
        Log.d(stateRefresh, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dViewmodel(content: String, className: String? = null) {
        Log.d(viewmodel, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dRepository(content: String, className: String? = null) {
        Log.d(repository, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dPermission(content: String, className: String? = null) {
        Log.d(permission, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dLoading(content: String, className: String? = null) {
        Log.d(loading, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

    fun dMultipleState(content: String, className: String? = null) {
        Log.d(multipleState, content + (if (className.isNullOrEmpty()) "" else "-$className"))
    }

}