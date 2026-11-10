package com.scrollmeter.app

import android.app.Application
import android.content.Context
import com.scrollmeter.app.data.AppDatabase
import com.scrollmeter.app.data.ScrollRepository
import com.scrollmeter.app.data.SettingsStore
import com.scrollmeter.app.ui.theme.AppFont
import com.scrollmeter.app.util.PermissionMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Manual dependency injection (PRD: "None (manual), small app").
 * One container, created once, shared by the screens and the accessibility service because
 * they run in the same process.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val repository: ScrollRepository by lazy { ScrollRepository(database.scrollDao()) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val permissions: PermissionMonitor by lazy { PermissionMonitor(appContext, settings) }

    /** "Today" as the screens see it; refreshed every time the app comes to the front. */
    val today = MutableStateFlow(LocalDate.now())
}

class ScrollMeterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppFont.init(this)
        container = AppContainer(this)
    }
}
