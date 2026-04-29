package com.camraw.app

import android.app.Application
import com.camraw.core.logging.CamrawLog

class CamrawApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CamrawLog.installFileSink(this)
    }
}
