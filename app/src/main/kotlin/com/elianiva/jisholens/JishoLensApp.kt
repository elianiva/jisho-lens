package com.elianiva.jisholens

import android.app.Application
import com.elianiva.jisholens.data.AppContainer

class JishoLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
