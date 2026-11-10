package com.scrollmeter.app.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colour tokens, copied from the design spec ("Foundations" and "Implementation notes"). */
object ScrollColors {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF17181A)
    val CardRaised = Color(0xFF24262A)
    val PillSelected = Color(0xFF2A2B2E)
    val Divider = Color(0xFF2A2C30)
    val Grid = Color(0xFF3A3C40)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextBody = Color(0xFFD7D9DC)
    val TextSecondary = Color(0xFFB4B7BC)
    val TextMuted = Color(0xFF8A8E94)
    val Link = Color(0xFF6AA6FF)
    val Facebook = Color(0xFF3D8BFF)
    val FacebookLite = Color(0xFF7FB2FF)
    val Instagram = Color(0xFFFF9F45)
    val BarSelected = Color(0xFF4C93FF)
    val BarIdle = Color(0xFF2E62B8)
    val Good = Color(0xFF5BD38A)
    val GoodBg = Color(0xFF1F3A2B)
    val Warn = Color(0xFFFFB46B)
    val Hud = Color(0xFF2BEA2B)
    val Heat = listOf(
        Color(0xFF2A2C30), Color(0xFF2B4170), Color(0xFF2C5BAE),
        Color(0xFF3375E6), Color(0xFF5AA0FF)
    )

    // One-off values named in the component, chart and state tables.
    val SoftText = Color(0xFFC9CBCF)          // range line, section labels, idle segment
    val Sentence = Color(0xFFE4E5E7)          // comparison sentence
    val SwitchOff = Color(0xFF4A4D52)
    val OnPrimary = Color(0xFF00122E)         // text on the blue primary button
    val Pressed = Color(0xFF2E3034)           // pressed secondary button, tooltip pill
    val DayLetterSelected = Color(0xFFE6E7E9)
    val BandDark = Color(0xFF0C0C0D)
    val BandLight = Color(0xFF1F2023)
    val AverageLine = Color(0xFF8E9298)
    val Tag = Color(0xFF5A5D63)               // "Avg." and "Peak" tags
    val LastWeekBar = Color(0xFF6B7078)
    val StoppedCard = Color(0xFF3A1D1D)
    val StoppedTitle = Color(0xFFFF8A80)
}

/**
 * Plus Jakarta Sans, if its TTF files are present in `res/font`, otherwise the system sans.
 *
 * The fonts are looked up by name at runtime so the project builds with or without them.
 * To enable the real font, add these four files (see README):
 * `plus_jakarta_sans_medium.ttf`, `_semibold.ttf`, `_bold.ttf`, `_extrabold.ttf`.
 */
object AppFont {
    var family: FontFamily = FontFamily.Default
        private set

    @SuppressLint("DiscouragedApi")
    fun init(context: Context) {
        val res = context.resources
        val pkg = context.packageName
        val fonts = ArrayList<Font>()
        val wanted = listOf(
            "plus_jakarta_sans_medium" to FontWeight.Medium,
            "plus_jakarta_sans_semibold" to FontWeight.SemiBold,
            "plus_jakarta_sans_bold" to FontWeight.Bold,
            "plus_jakarta_sans_extrabold" to FontWeight.ExtraBold,
        )
        for ((name, weight) in wanted) {
            val id = res.getIdentifier(name, "font", pkg)
            if (id != 0) fonts.add(Font(id, weight))
        }
        if (fonts.isNotEmpty()) family = FontFamily(fonts)
    }
}

/** Type scale from the design spec ("Typography"). Sizes in sp. */
object ScrollType {
    private fun style(size: Int, weight: Int, spacing: Float = 0f) = TextStyle(
        fontFamily = AppFont.family,
        fontSize = size.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = spacing.sp,
    )

    val display get() = style(44, 800, -1f)          // hero number on a card
    val displaySmall get() = style(40, 800, -1f)     // second-card number, weekly average
    val appTitle get() = style(30, 800, -0.5f)       // "Scroll Meter"
    val screenTitle get() = style(28, 800, -0.5f)    // "Weekly report", "Thu, 1 Oct"
    val statValue get() = style(22, 800)
    val cardTitle get() = style(18, 700)
    val rowTitle get() = style(16, 600)
    val body get() = style(16, 500)
    val bodySmall get() = style(14, 500)
    val caption get() = style(13, 500)
    val micro get() = style(12, 500)

    /** For the handful of one-off sizes in the screen specs (15, 17, 18, 20 sp). */
    fun custom(size: Int, weight: Int) = style(size, weight)
}

@Composable
fun ScrollMeterTheme(content: @Composable () -> Unit) {
    // Dark only in v1.
    val scheme = darkColorScheme(
        primary = ScrollColors.Facebook,
        onPrimary = ScrollColors.OnPrimary,
        background = ScrollColors.Bg,
        onBackground = ScrollColors.TextPrimary,
        surface = ScrollColors.Card,
        onSurface = ScrollColors.TextPrimary,
        onSurfaceVariant = ScrollColors.TextSecondary,
        surfaceContainer = ScrollColors.CardRaised,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
