package org.jetbrains.compose.resources

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale

internal fun applePreferredLocales(): List<LocaleQualifiers> {
    val defaults = NSUserDefaults.standardUserDefaults
    val tags = defaults.stringArrayForKey("AppleLanguages")
        ?.mapNotNull { it as? String }
        .orEmpty()
    if (tags.isNotEmpty()) {
        return tags.map { localeQualifiersOf(NSLocale(it)) }
    }
    // AppleLanguages is the preferred-language list. AppleLocale is the current locale id.
    val current = defaults.stringForKey("AppleLocale")
    if (current != null) {
        return listOf(localeQualifiersOf(NSLocale(current)))
    }
    return listOf(localeQualifiersOf(NSLocale.currentLocale))
}

private fun localeQualifiersOf(loc: NSLocale): LocaleQualifiers {
    val languageCode = (loc.objectForKey(NSLocaleLanguageCode) as? String).orEmpty()
    val scriptCode = (loc.objectForKey(NSLocaleScriptCode) as? String).orEmpty()
    val regionCode = (loc.objectForKey(NSLocaleCountryCode) as? String).orEmpty()
    return LocaleQualifiers(
        language = LanguageQualifier(languageCode),
        script = ScriptQualifier(scriptCode),
        region = RegionQualifier(regionCode)
    )
}
