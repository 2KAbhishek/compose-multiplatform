package org.jetbrains.compose.resources

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.*

internal actual fun getSystemEnvironment(): ResourceEnvironment {
    val configuration = Resources.getSystem().configuration
    val isDarkTheme = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val dpi = configuration.densityDpi
    val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !configuration.locales.isEmpty) {
        val localeList = configuration.locales
        (0 until localeList.size()).map { i -> localeQualifiersOf(localeList.get(i)) }
    } else {
        @Suppress("DEPRECATION")
        listOf(localeQualifiersOf(configuration.locale))
    }
    return ResourceEnvironment(
        locales = locales,
        theme = ThemeQualifier.selectByValue(isDarkTheme),
        density = DensityQualifier.selectByValue(dpi)
    )
}

private fun localeQualifiersOf(locale: Locale) = LocaleQualifiers(
    language = LanguageQualifier(locale.language.orEmpty()),
    script = ScriptQualifier(locale.script.orEmpty()),
    region = RegionQualifier(locale.country.orEmpty())
)
