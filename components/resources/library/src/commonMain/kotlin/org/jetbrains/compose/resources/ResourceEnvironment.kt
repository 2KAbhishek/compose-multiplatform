package org.jetbrains.compose.resources

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList

class ResourceEnvironment internal constructor(
    internal val locales: List<LocaleQualifiers>,
    internal val theme: ThemeQualifier,
    internal val density: DensityQualifier
) {
    internal val language: LanguageQualifier get() = locales.firstOrNull()?.language ?: LanguageQualifier("")
    internal val script: ScriptQualifier get() = locales.firstOrNull()?.script ?: ScriptQualifier("")
    internal val region: RegionQualifier get() = locales.firstOrNull()?.region ?: RegionQualifier("")

    internal constructor(
        language: LanguageQualifier,
        script: ScriptQualifier,
        region: RegionQualifier,
        theme: ThemeQualifier,
        density: DensityQualifier
    ) : this(
        locales = listOf(LocaleQualifiers(language, script, region)),
        theme = theme,
        density = density
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ResourceEnvironment

        if (locales != other.locales) return false
        if (theme != other.theme) return false
        if (density != other.density) return false

        return true
    }

    override fun hashCode(): Int {
        var result = locales.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + density.hashCode()
        return result
    }
}

internal interface ComposeEnvironment {
    @Composable
    fun rememberEnvironment(): ResourceEnvironment
}

internal val DefaultComposeEnvironment = object : ComposeEnvironment {
    @Composable
    override fun rememberEnvironment(): ResourceEnvironment {
        val composeLocales = LocaleList.current
        val composeTheme = isSystemInDarkTheme()
        val composeDensity = LocalDensity.current

        //cache ResourceEnvironment unless compose environment is changed
        return remember(composeLocales, composeTheme, composeDensity) {
            val composeMapped = if (composeLocales.isEmpty()) {
                val single = Locale.current
                listOf(
                    LocaleQualifiers(
                        LanguageQualifier(single.language),
                        ScriptQualifier(single.script),
                        RegionQualifier(single.region)
                    )
                )
            } else {
                composeLocales.map {
                    LocaleQualifiers(
                        LanguageQualifier(it.language),
                        ScriptQualifier(it.script),
                        RegionQualifier(it.region)
                    )
                }
            }
            val locales = selectResourceLocales(composeMapped, getResourceEnvironment().locales)
            ResourceEnvironment(
                locales = locales,
                theme = ThemeQualifier.selectByValue(composeTheme),
                density = DensityQualifier.selectByDensity(composeDensity.density)
            )
        }
    }
}

//ComposeEnvironment provider will be overridden for tests
internal val LocalComposeEnvironment = staticCompositionLocalOf { DefaultComposeEnvironment }

/**
 * Returns an instance of [ResourceEnvironment].
 *
 * The [ResourceEnvironment] class represents the environment for resources.
 *
 * @return An instance of [ResourceEnvironment] representing the current environment.
 */
@Composable
fun rememberResourceEnvironment(): ResourceEnvironment {
    val composeEnvironment = LocalComposeEnvironment.current
    return composeEnvironment.rememberEnvironment()
}

/**
 * Chooses the locale list used for resource matching.
 *
 * [androidx.compose.ui.text.intl.LocaleList] is used as-is when it already contains a
 * fallback chain (size > 1). That is the Android and iOS case.
 *
 * On JVM desktop, [LocaleList] is often a single platform-default locale even when the OS
 * has a preference list. When Compose reports only one locale and the OS has more, the OS
 * list is used.
 *
 * A single Compose locale whose language is not in the OS list is treated as an explicit
 * override and is prepended to the OS list.
 */
internal fun selectResourceLocales(
    composeLocales: List<LocaleQualifiers>,
    systemLocales: List<LocaleQualifiers>
): List<LocaleQualifiers> {
    if (composeLocales.size > 1) return composeLocales
    if (systemLocales.size <= 1) return composeLocales.ifEmpty { systemLocales }
    val compose = composeLocales.singleOrNull() ?: return systemLocales
    val languageInSystem = systemLocales.any { it.language == compose.language }
    if (!languageInSystem) return listOf(compose) + systemLocales
    return systemLocales
}

internal expect fun getSystemEnvironment(): ResourceEnvironment

//the function reference will be overridden for tests
//@TestOnly
internal var getResourceEnvironment = ::getSystemEnvironment

/**
 * Provides the resource environment for non-composable access to resources.
 * It is an expensive operation! Don't use it in composable functions with no cache!
 */
fun getSystemResourceEnvironment(): ResourceEnvironment = getResourceEnvironment()

@OptIn(InternalResourceApi::class)
internal fun Resource.getResourceItemByEnvironment(environment: ResourceEnvironment): ResourceItem {
    //Priority of environments: https://developer.android.com/guide/topics/resources/providing-resources#table2
    items.toList()
        .filterByLocales(environment.locales)
        .also { if (it.size == 1) return it.first() }
        .filterBy(environment.theme)
        .also { if (it.size == 1) return it.first() }
        .filterByDensity(environment.density)
        .also { if (it.size == 1) return it.first() }
        .let { items ->
            if (items.isEmpty()) {
                error("Resource with ID='$id' not found")
            } else {
                error("Resource with ID='$id' has more than one file: ${items.joinToString { it.path }}")
            }
        }
}

private fun List<ResourceItem>.filterBy(qualifier: Qualifier): List<ResourceItem> {
    //Android has a slightly different algorithm,
    //but it provides the same result: https://developer.android.com/guide/topics/resources/providing-resources#BestMatch

    //filter items with the requested qualifier
    val withQualifier = filter { item ->
        item.qualifiers.any { it == qualifier }
    }

    if (withQualifier.isNotEmpty()) return withQualifier

    //items with no requested qualifier type (default)
    return filter { item ->
        item.qualifiers.none { it::class == qualifier::class }
    }
}

// https://developer.android.com/guide/topics/resources/providing-resources#BestMatch
// In general, Android prefers scaling down a larger original image to scaling up a smaller original image.
private fun List<ResourceItem>.filterByDensity(density: DensityQualifier): List<ResourceItem> {
    val items = this
    var withQualifier = emptyList<ResourceItem>()

    // filter with the same or better density
    val exactAndHigherQualifiers = DensityQualifier.entries
        .filter { it.dpi >= density.dpi }
        .sortedBy { it.dpi }

    for (qualifier in exactAndHigherQualifiers) {
        withQualifier = items.filter { item -> item.qualifiers.any { it == qualifier } }
        if (withQualifier.isNotEmpty()) break
    }
    if (withQualifier.isNotEmpty()) return withQualifier

    // filter with low density
    val lowQualifiers = DensityQualifier.entries
        .minus(DensityQualifier.LDPI)
        .filter { it.dpi < density.dpi }
        .sortedByDescending { it.dpi }
    for (qualifier in lowQualifiers) {
        withQualifier = items.filter { item -> item.qualifiers.any { it == qualifier } }
        if (withQualifier.isNotEmpty()) break
    }
    if (withQualifier.isNotEmpty()) return withQualifier

    //items with no DensityQualifier (default)
    // The system assumes that default resources (those from a directory without configuration qualifiers)
    // are designed for the baseline pixel density (mdpi) and resizes those bitmaps
    // to the appropriate size for the current pixel density.
    // https://developer.android.com/training/multiscreen/screendensities#DensityConsiderations
    val withNoDensity = items.filter { item ->
        item.qualifiers.none { it is DensityQualifier }
    }
    if (withNoDensity.isNotEmpty()) return withNoDensity

    //items with LDPI density
    return items.filter { item ->
        item.qualifiers.any { it == DensityQualifier.LDPI }
    }
}

// Filter by prioritized list of locales.
// For each locale in the prioritized list (e.g. [fr-CA, es-US, en-US]):
// 1) exact language + script + region -> use it
// 2) language + script (no region) -> use it
// 3) language + region (no script) -> use it
// 4) language only (no script, no region) -> use it
// 5) same language, other region (Android parent-locale), when allowed
// If none of the preferred locales match:
// 6) items with NO locale qualifiers at all (default)
// When the environment script is empty, prefer items without
// a ScriptQualifier first; fall back to script-tagged items only if nothing else matches.
// issue: https://github.com/JetBrains/compose-multiplatform/issues/4571
private fun List<ResourceItem>.filterByLocales(
    locales: List<LocaleQualifiers>
): List<ResourceItem> {
    for (locale in locales) {
        val matched = filterBySingleLocale(locale.language, locale.script, locale.region)
        if (!matched.isNullOrEmpty()) {
            return matched
        }
    }

    // Default: items with NO locale qualifiers at all
    return filter { item ->
        item.qualifiers.none { it is LanguageQualifier || it is ScriptQualifier || it is RegionQualifier }
    }
}

private fun ResourceItem.scriptQualifier(): ScriptQualifier? =
    qualifiers.filterIsInstance<ScriptQualifier>().firstOrNull()

private fun ResourceItem.regionQualifier(): RegionQualifier? =
    qualifiers.filterIsInstance<RegionQualifier>().firstOrNull()

// CLDR likely subtags used when a values-* folder omits script (e.g. values-zh-rTW).
private fun likelyScript(language: String, region: String): String? = when (language) {
    "zh" -> if (region in TRADITIONAL_CHINESE_REGIONS) "Hant" else "Hans"
    else -> null
}

private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

private fun ResourceItem.compatibleWithRequestedScript(requested: ScriptQualifier): Boolean {
    val explicit = scriptQualifier()
    if (explicit != null) return explicit == requested
    val inferred = likelyScript(
        language = qualifiers.filterIsInstance<LanguageQualifier>().firstOrNull()?.language.orEmpty(),
        region = regionQualifier()?.region.orEmpty()
    )
    return inferred == null || inferred == requested.script
}

private fun List<ResourceItem>.narrowByRegion(
    region: RegionQualifier,
    allowOtherRegions: Boolean
): List<ResourceItem> {
    if (isEmpty()) return emptyList()
    val regionCode = region.region
    if (regionCode.isNotEmpty()) {
        val exact = filter { it.regionQualifier()?.region == regionCode }
        if (exact.isNotEmpty()) return exact
    }
    val noRegion = filter { it.regionQualifier() == null }
    if (noRegion.isNotEmpty()) return noRegion
    if (allowOtherRegions) {
        val otherRegions = filter { it.regionQualifier() != null }
        if (otherRegions.size == 1) return otherRegions
        if (otherRegions.isNotEmpty()) {
            // Android parent-locale matching: any same-language region is a match.
            // Pick one file so matching does not fail with "more than one file".
            return listOf(
                otherRegions.minWith(
                    compareBy({ it.regionQualifier()!!.region }, { it.path })
                )
            )
        }
    }
    return emptyList()
}

private fun List<ResourceItem>.filterBySingleLocale(
    language: LanguageQualifier,
    script: ScriptQualifier,
    region: RegionQualifier
): List<ResourceItem>? {
    val withLanguage = filter { item ->
        item.qualifiers.any { it == language }
    }
    if (withLanguage.isEmpty()) return null

    if (!script.isEmpty()) {
        val explicitScript = withLanguage.filter { it.scriptQualifier() == script }
        val explicitMatch = explicitScript.narrowByRegion(region, allowOtherRegions = false)
        if (explicitMatch.isNotEmpty()) return explicitMatch

        val inferredScript = withLanguage.filter { item ->
            item.scriptQualifier() == null && item.compatibleWithRequestedScript(script)
        }
        val inferredMatch = inferredScript.narrowByRegion(region, allowOtherRegions = true)
        if (inferredMatch.isNotEmpty()) return inferredMatch

        return null
    }

    val withDefaultScript = withLanguage.filter { it.scriptQualifier() == null }
    val defaultScriptMatch = withDefaultScript.narrowByRegion(region, allowOtherRegions = true)
    if (defaultScriptMatch.isNotEmpty()) return defaultScriptMatch

    val byRegion = withLanguage.narrowByRegion(region, allowOtherRegions = true)
    if (byRegion.isNotEmpty()) return byRegion

    return null
}

internal fun List<ResourceItem>.filterByLocale(
    language: LanguageQualifier,
    script: ScriptQualifier,
    region: RegionQualifier
): List<ResourceItem> = filterByLocales(listOf(LocaleQualifiers(language, script, region)))
