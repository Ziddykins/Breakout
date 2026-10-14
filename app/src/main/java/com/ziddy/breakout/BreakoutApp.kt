package com.ziddy.breakout

import android.app.Application
import com.ziddy.breakout.shell.ShizukuShell

class BreakoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuShell.init(this)
    }
}
