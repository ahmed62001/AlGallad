package com.algallad.store

import android.app.Application
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class AlGalladApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckSetup.install(this)
    }
}

fun AppCompatActivity.secureAuthScreen() {
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
}
