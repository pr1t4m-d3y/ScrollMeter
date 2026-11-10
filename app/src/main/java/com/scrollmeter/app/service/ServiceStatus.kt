package com.scrollmeter.app.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live facts about the running accessibility service, readable by the screens.
 * The service and the app share one process, so a plain in-memory object is enough.
 */
object ServiceStatus {

    /** True while Android has the accessibility service bound. */
    val connected = MutableStateFlow(false)

    /**
     * True when the normal accessibility overlay window could not be added and the fallback
     * needs "Display over other apps", which is not granted (design spec "Missing permission").
     */
    val hudNeedsOverlayPermission = MutableStateFlow(false)
}
