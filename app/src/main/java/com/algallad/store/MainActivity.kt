package com.algallad.store

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.algallad.store.data.ThemeMode
import com.algallad.store.data.fontScaleFlow
import com.algallad.store.data.readFontScale
import com.algallad.store.data.readThemeMode
import com.algallad.store.data.themeModeFlow
import com.algallad.store.ui.AppRoot
import com.algallad.store.ui.theme.AlGalladTheme
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Cheap email-verification recheck: at most one refresh per resume, only when signed
        // in and still unverified, so verified admins stop looking like customers.
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null && !user.isEmailVerified) vm.refreshSession()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Task 4 — no wrong-theme flash on cold start: blocking first-read of the
        // persisted choice BEFORE setContent (DataStore first read hits disk once;
        // subsequent updates flow reactively). XML theme parent stays
        // Theme.AppCompat.DayNight.NoActionBar (crash fix, untouched).
        val startMode: ThemeMode = runBlocking { readThemeMode(applicationContext) }
        // Round 6 (Task B): app text-size preference read up-front (no first-frame flash).
        val startFontScale: Float = runBlocking { readFontScale(applicationContext) }
        // Edge-to-edge once; per-theme status/navigation icon appearance is driven
        // from Compose (AppRoot ThemeIcons effect) so it follows instant switches.
        enableEdgeToEdge()
        setContent {
            val mode by themeModeFlow(applicationContext).collectAsState(initial = startMode)
            val fontScale by fontScaleFlow(applicationContext).collectAsState(initial = startFontScale)
            AlGalladTheme(mode = mode, fontScale = fontScale) { AppRoot(vm) }
        }
    }
}

