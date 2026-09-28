package org.jetbrains.compose.resources

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.NSLocaleScriptCode
import platform.Foundation.NSUserDefaults

internal fun applePreferredLocales(): List<LocaleQualifiers> {
    val tags = NSUserDefaults.standardUserDefaults.stringArrayForKey("AppleLanguages")
        ?.mapNotNull { it as? String }
        .orEmpty()
    return tags.map { localeQualifiersOf(NSLocale(it)) }.ifEmpty {
        listOf(localeQualifiersOf(NSLocale("en")))
    }
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
