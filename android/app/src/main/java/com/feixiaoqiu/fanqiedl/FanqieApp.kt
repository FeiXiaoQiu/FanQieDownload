package com.feixiaoqiu.fanqiedl

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.feixiaoqiu.fanqiedl.data.AppContainer
import okhttp3.OkHttpClient

class FanqieApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                            )
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
    }
}
