package com.scrollmeter.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrollmeter.app.ui.day.DayScreen
import com.scrollmeter.app.ui.day.DayViewModel
import com.scrollmeter.app.ui.settings.SettingsScreen
import com.scrollmeter.app.ui.settings.SettingsViewModel
import com.scrollmeter.app.ui.theme.ScrollColors
import com.scrollmeter.app.ui.theme.ScrollMeterTheme
import com.scrollmeter.app.ui.today.TodayScreen
import com.scrollmeter.app.ui.today.TodayViewModel
import com.scrollmeter.app.ui.weekly.WeeklyScreen
import com.scrollmeter.app.ui.weekly.WeeklyViewModel
import com.scrollmeter.app.util.PermissionChecklist
import com.scrollmeter.app.util.PermissionIntents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The four screens of the dashboard. */
sealed interface Screen {
    data object Today : Screen
    data class Day(val date: LocalDate) : Screen
    data object Weekly : Screen
    data object Settings : Screen
}

/**
 * Navigation is a plain back stack (no navigation library; the app has four screens).
 * It lives in a ViewModel so it survives rotation.
 */
class NavViewModel(container: AppContainer) : ViewModel() {

    val stack = mutableStateListOf<Screen>(Screen.Today)

    /** False until we know whether Setup must open first, so Today never flashes by. */
    var ready by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // Screen 4 rule: Setup opens on launch until all five permission rows are done.
            val settings = container.settings.settings.first()
            val system = container.permissions.readQuick()
            val checklist = PermissionChecklist.from(system, settings, PermissionIntents.sdkInt)
            if (!checklist.allDone) stack.add(Screen.Settings)
            ready = true
        }
    }

    fun push(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun replaceTop(screen: Screen) {
        if (stack.isNotEmpty()) stack[stack.size - 1] = screen
    }
}

/** Minimal ViewModel factory for manual dependency injection. */
class SimpleFactory<T : ViewModel>(private val builder: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = builder() as VM
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ScrollMeterTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as ScrollMeterApp).container
        // FR8.1: re-check permissions every time the app opens or returns from Settings.
        container.permissions.refresh()
        container.today.value = LocalDate.now()
    }
}

@Composable
private fun AppRoot() {
    val container = (LocalContext.current.applicationContext as ScrollMeterApp).container
    val nav: NavViewModel = viewModel(factory = remember { SimpleFactory { NavViewModel(container) } })
    val today by container.today.collectAsState()

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrollColors.Bg)
            .systemBarsPadding(),
    ) {
        // Until we know whether Setup must open first, show only the black background.
        if (nav.ready) {
            when (val screen = nav.stack.last()) {
                Screen.Today -> {
                    val vm: TodayViewModel = viewModel(
                        factory = remember {
                            SimpleFactory {
                                TodayViewModel(container.repository, container.today, container.permissions.state)
                            }
                        },
                    )
                    TodayScreen(
                        viewModel = vm,
                        onOpenSettings = { nav.push(Screen.Settings) },
                        onOpenWeekly = { nav.push(Screen.Weekly) },
                        onOpenDay = { nav.push(Screen.Day(it)) },
                    )
                }

                is Screen.Day -> {
                    val vm: DayViewModel = viewModel(
                        factory = remember { SimpleFactory { DayViewModel(container.repository, screen.date) } },
                    )
                    DayScreen(
                        viewModel = vm,
                        date = screen.date,
                        today = today,
                        onBack = { nav.pop() },
                        onChangeDate = { nav.replaceTop(Screen.Day(it)) },
                    )
                }

                Screen.Weekly -> {
                    val vm: WeeklyViewModel = viewModel(
                        factory = remember { SimpleFactory { WeeklyViewModel(container.repository, container.today) } },
                    )
                    WeeklyScreen(
                        viewModel = vm,
                        onBack = { nav.pop() },
                        onOpenDay = { nav.push(Screen.Day(it)) },
                    )
                }

                Screen.Settings -> {
                    val vm: SettingsViewModel = viewModel(
                        factory = remember { SimpleFactory { SettingsViewModel(container.settings, container.permissions.state) } },
                    )
                    SettingsScreen(viewModel = vm, onBack = { nav.pop() })
                }
            }
        }
    }
}
