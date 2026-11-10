package com.scrollmeter.app.model

/**
 * The only apps Scroll Meter ever tracks (PRD "Non-goals": nothing else in v1).
 *
 * [key] is the value stored in the database `app` column.
 */
enum class WatchedApp(val key: String, val packageName: String, val label: String) {
    FACEBOOK("facebook", "com.facebook.katana", "Facebook"),
    FACEBOOK_LITE("facebook_lite", "com.facebook.lite", "Facebook Lite"),
    INSTAGRAM("instagram", "com.instagram.android", "Instagram");

    /** Colour family used on every chart: Facebook Lite is drawn as Facebook (blue). */
    val group: AppGroup
        get() = if (this == INSTAGRAM) AppGroup.INSTAGRAM else AppGroup.FACEBOOK

    companion object {
        /** Hot path: no allocation when [pkg] is already a String (it always is in practice). */
        fun fromPackage(pkg: CharSequence?): WatchedApp? {
            if (pkg == null) return null
            val name = pkg.toString()
            if (name == FACEBOOK.packageName) return FACEBOOK
            if (name == INSTAGRAM.packageName) return INSTAGRAM
            if (name == FACEBOOK_LITE.packageName) return FACEBOOK_LITE
            return null
        }

        fun fromKey(key: String): WatchedApp? {
            for (app in entries) if (app.key == key) return app
            return null
        }
    }
}

/** The two colour groups shown on charts: Facebook is always blue, Instagram always orange. */
enum class AppGroup { FACEBOOK, INSTAGRAM }
