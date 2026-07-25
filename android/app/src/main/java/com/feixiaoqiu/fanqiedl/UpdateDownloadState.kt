package com.feixiaoqiu.fanqiedl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UpdateDownloadState {
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun start() {
        _downloading.value = true
        _progress.value = 0f
        _message.value = "准备下载…"
    }

    fun progress(pct: Float, msg: String) {
        _downloading.value = true
        _progress.value = pct
        _message.value = msg
    }

    fun complete() {
        _downloading.value = false
        _progress.value = 1f
        _message.value = "下载完成"
    }

    fun error(msg: String) {
        _downloading.value = false
        _message.value = "下载失败：$msg"
    }

    fun reset() {
        _downloading.value = false
        _progress.value = 0f
        _message.value = null
    }
}
