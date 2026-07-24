package com.feixiaoqiu.fanqiedl

import android.app.Application
import com.feixiaoqiu.fanqiedl.data.AppContainer

class FanqieApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
