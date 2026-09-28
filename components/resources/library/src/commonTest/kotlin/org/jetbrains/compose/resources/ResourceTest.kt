/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.resources

import org.jetbrains.compose.resources.DensityQualifier.*
import org.jetbrains.compose.resources.ThemeQualifier.DARK
import org.jetbrains.compose.resources.ThemeQualifier.LIGHT
import androidx.compose.ui.text.intl.Locale
import kotlin.test.*

class ResourceTest {
    @Test
    fun testResourceEquals() {
        assertEquals(TestDrawableResource("a"), TestDrawableResource("a"))
    }

    @Test
    fun testResourceNotEquals() {
        assertNotEquals(TestDrawableResource("a"), TestDrawableResource("b"))
    }

    @Test
    fun testGetPathByEnvironment() {
        val resource = DrawableResource(
            id = "ImageResource:test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en")), "en", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en"), RegionQualifier("US"), XHDPI), "en-rUS-xhdpi", -1, -1),
                ResourceItem(setOf(LanguageQualifier("de"), RegionQualifier("US")), "de-rUS", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr"), LIGHT), "fr-light", -1, -1),
                ResourceItem(setOf(DARK), "dark", -1, -1),
            )
        )
        fun env(lang: String, reg: String, theme: ThemeQualifier, density: DensityQualifier) = ResourceEnvironment(
            language = LanguageQualifier(lang),
            script = ScriptQualifier(""),
            region = RegionQualifier(reg),
            theme = theme,
            density = density
        )
        assertEquals(
            "en-rUS-xhdpi",
            resource.getResourceItemByEnvironment(env("en", "US", DARK, XXHDPI)).path
        )
        assertEquals(
            "en",
            resource.getResourceItemByEnvironment(env("en", "IN", LIGHT, LDPI)).path
        )
        assertEquals(
            "de-rUS",
            resource.getResourceItemByEnvironment(env("de", "US", LIGHT, LDPI)).path
        )
        assertEquals(
            "de-rUS",
            resource.getResourceItemByEnvironment(env("de", "", LIGHT, LDPI)).path
        )
        assertEquals(
            "de-rUS",
            resource.getResourceItemByEnvironment(env("de", "IN", LIGHT, LDPI)).path
        )
        assertEquals(
            "default",
            resource.getResourceItemByEnvironment(env("ch", "", LIGHT, MDPI)).path
        )
        assertEquals(
            "dark",
            resource.getResourceItemByEnvironment(env("ch", "", DARK, MDPI)).path
        )
        assertEquals(
            "fr-light",
            resource.getResourceItemByEnvironment(env("fr", "", DARK, MDPI)).path
        )
        assertEquals(
            "fr-light",
            resource.getResourceItemByEnvironment(env("fr", "IN", LIGHT, MDPI)).path
        )
        assertEquals(
            "default",
            resource.getResourceItemByEnvironment(env("ru", "US", LIGHT, XHDPI)).path
        )
        assertEquals(
            "dark",
            resource.getResourceItemByEnvironment(env("ru", "US", DARK, XHDPI)).path
        )

        val resourceWithNoDefault = DrawableResource(
            id = "ImageResource:test2",
            items = setOf(
                ResourceItem(setOf(LanguageQualifier("en")), "en", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr"), LIGHT), "fr-light", -1, -1)
            )
        )
        assertFailsWith<IllegalStateException> {
            resourceWithNoDefault.getResourceItemByEnvironment(env("ru", "US", DARK, XHDPI))
        }.message.let { msg ->
            assertEquals("Resource with ID='ImageResource:test2' not found", msg)
        }

        val resourceWithFewFiles = DrawableResource(
            id = "ImageResource:test3",
            items = setOf(
                ResourceItem(setOf(LanguageQualifier("en")), "en1", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en")), "en2", -1, -1)
            )
        )
        assertFailsWith<IllegalStateException> {
            resourceWithFewFiles.getResourceItemByEnvironment(env("en", "US", DARK, XHDPI))
        }.message.let { msg ->
            assertEquals("Resource with ID='ImageResource:test3' has more than one file: en1, en2", msg)
        }
    }

    @Test
    fun testGetPathByEnvironmentWithScript() {
        val resource = DrawableResource(
            id = "ImageResource:script_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr")), "sr", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), ScriptQualifier("Latn")), "sr-Latn", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), ScriptQualifier("Cyrl")), "sr-Cyrl", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), ScriptQualifier("Latn"), RegionQualifier("RS")), "sr-Latn-RS", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), RegionQualifier("RS")), "sr-RS", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), ScriptQualifier("Hans")), "zh-Hans", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), ScriptQualifier("Hant")), "zh-Hant", -1, -1),
            )
        )
        fun env(lang: String, script: String, reg: String) = ResourceEnvironment(
            language = LanguageQualifier(lang),
            script = ScriptQualifier(script),
            region = RegionQualifier(reg),
            theme = LIGHT,
            density = XHDPI
        )

        // case 1: language + script match (narrowed by region if possible)
        assertEquals(
            "sr-Latn-RS",
            resource.getResourceItemByEnvironment(env("sr", "Latn", "RS")).path
        )
        assertEquals(
            "sr-Latn",
            resource.getResourceItemByEnvironment(env("sr", "Latn", "")).path
        )
        assertEquals(
            "sr-Latn",
            resource.getResourceItemByEnvironment(env("sr", "Latn", "BA")).path
        )
        assertEquals(
            "sr-Cyrl",
            resource.getResourceItemByEnvironment(env("sr", "Cyrl", "")).path
        )
        assertEquals(
            "zh-Hans",
            resource.getResourceItemByEnvironment(env("zh", "Hans", "")).path
        )
        assertEquals(
            "zh-Hant",
            resource.getResourceItemByEnvironment(env("zh", "Hant", "")).path
        )

        // case 2: language match without script (narrowed by region if possible)
        assertEquals(
            "sr-RS",
            resource.getResourceItemByEnvironment(env("sr", "", "RS")).path
        )
        assertEquals(
            "sr",
            resource.getResourceItemByEnvironment(env("sr", "", "")).path
        )

        // case 3: env-requested script must not cross to a different one (Hans vs Hant)
        val scriptedOnlyResource = DrawableResource(
            id = "ImageResource:scripted_only",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), ScriptQualifier("Cyrl"), RegionQualifier("RS")), "sr-Cyrl-RS", -1, -1),
            )
        )
        assertEquals(
            "default",
            scriptedOnlyResource.getResourceItemByEnvironment(env("sr", "Latn", "RS")).path
        )

        val zhSimplifiedTraditional = DrawableResource(
            id = "ImageResource:zh_hans_hant",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), ScriptQualifier("Hans"), RegionQualifier("CN")), "zh-Hans-CN", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), ScriptQualifier("Hant"), RegionQualifier("TW")), "zh-Hant-TW", -1, -1),
            )
        )
        assertEquals(
            "default",
            zhSimplifiedTraditional.getResourceItemByEnvironment(env("zh", "Hans", "TW")).path
        )

        // case 3 (variant): empty environment script does fall back across scripts
        val scriptOnlyResource = DrawableResource(
            id = "ImageResource:script_only",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("sr"), ScriptQualifier("Latn")), "sr-Latn", -1, -1),
            )
        )
        assertEquals(
            "sr-Latn",
            scriptOnlyResource.getResourceItemByEnvironment(env("sr", "", "")).path
        )

        // case 4: no language match -> default
        assertEquals(
            "default",
            resource.getResourceItemByEnvironment(env("en", "", "US")).path
        )
    }

    @Test
    fun testGetPathByPrioritizedLocales() {
        val resource = DrawableResource(
            id = "ImageResource:multilocale_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en")), "en", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en"), RegionQualifier("US")), "en-US", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr"), RegionQualifier("FR")), "fr-FR", -1, -1),
                ResourceItem(setOf(LanguageQualifier("es")), "es", -1, -1),
                ResourceItem(setOf(LanguageQualifier("es"), RegionQualifier("ES")), "es-ES", -1, -1),
                ResourceItem(setOf(LanguageQualifier("de")), "de", -1, -1),
            )
        )

        fun multiLocaleEnv(vararg locales: LocaleQualifiers) = ResourceEnvironment(
            locales = locales.toList(),
            theme = LIGHT,
            density = MDPI
        )

        // Case 1: First preferred locale matches exact region
        assertEquals(
            "fr-FR",
            resource.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("fr", "", "FR"),
                    LocaleQualifiers("es", "", "ES"),
                    LocaleQualifiers("en", "", "US")
                )
            ).path
        )

        // Case 2: Same language, other region still matches (Android parent-locale).
        // fr-CA with only fr-FR must not skip French for Spanish.
        assertEquals(
            "fr-FR",
            resource.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("fr", "", "CA"),
                    LocaleQualifiers("es", "", "MX"),
                    LocaleQualifiers("en", "", "US")
                )
            ).path
        )

        // Case 3: First preferred locale (it-IT) has no match, second preferred locale (pt-BR) has no match,
        // third preferred locale (de-DE) matches base "de"
        assertEquals(
            "de",
            resource.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("it", "", "IT"),
                    LocaleQualifiers("pt", "", "BR"),
                    LocaleQualifiers("de", "", "DE")
                )
            ).path
        )

        // Case 4: None of the preferred locales match -> fallback to default
        assertEquals(
            "default",
            resource.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("ja", "", "JP"),
                    LocaleQualifiers("ko", "", "KR"),
                    LocaleQualifiers("ru", "", "RU")
                )
            ).path
        )

        // Case 5: Regional fallback within primary language takes precedence over secondary language
        // (fr-CA matches base "fr" when base "fr" exists)
        val resourceWithBaseFr = DrawableResource(
            id = "ImageResource:base_fr_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr")), "fr", -1, -1),
                ResourceItem(setOf(LanguageQualifier("es"), RegionQualifier("ES")), "es-ES", -1, -1)
            )
        )
        assertEquals(
            "fr",
            resourceWithBaseFr.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("fr", "", "CA"),
                    LocaleQualifiers("es", "", "ES")
                )
            ).path
        )

        // Case 6: Script isolation across prioritized locales
        // zh-Hans-CN must not cross to zh-Hant; it should advance to second preferred locale (en)
        val resourceWithScript = DrawableResource(
            id = "ImageResource:script_fallback_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), ScriptQualifier("Hant")), "zh-Hant", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en")), "en", -1, -1)
            )
        )
        assertEquals(
            "en",
            resourceWithScript.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("zh", "Hans", "CN"),
                    LocaleQualifiers("en", "", "US")
                )
            ).path
        )

        // Case 7: English tagged as values-en beats a later Spanish locale
        val resourceWithEnAndEs = DrawableResource(
            id = "ImageResource:en_es_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("en")), "en", -1, -1),
                ResourceItem(setOf(LanguageQualifier("es")), "es", -1, -1),
            )
        )
        assertEquals(
            "en",
            resourceWithEnAndEs.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("en", "", "IN"),
                    LocaleQualifiers("es", "", "US")
                )
            ).path
        )

        // Case 8: Unqualified values/ is last resort, not English. en-IN then es → es
        val resourceDefaultAndEs = DrawableResource(
            id = "ImageResource:default_es_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("es")), "es", -1, -1),
            )
        )
        assertEquals(
            "es",
            resourceDefaultAndEs.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("en", "", "IN"),
                    LocaleQualifiers("es", "", "US")
                )
            ).path
        )

        // Case 9: Region-only Chinese folders pick likely script (CLDR)
        val resourceZhRegionOnly = DrawableResource(
            id = "ImageResource:zh_region_test",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), RegionQualifier("CN")), "zh-rCN", -1, -1),
                ResourceItem(setOf(LanguageQualifier("zh"), RegionQualifier("TW")), "zh-rTW", -1, -1),
            )
        )
        assertEquals(
            "zh-rTW",
            resourceZhRegionOnly.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("zh", "Hant", "US"),
                    LocaleQualifiers("en", "", "US")
                )
            ).path
        )
        assertEquals(
            "zh-rCN",
            resourceZhRegionOnly.getResourceItemByEnvironment(
                multiLocaleEnv(
                    LocaleQualifiers("zh", "Hans", ""),
                    LocaleQualifiers("en", "", "US")
                )
            ).path
        )

        // Case 10: several other-region folders must resolve to one file, not "more than one file"
        val resourceSiblingRegions = DrawableResource(
            id = "ImageResource:sibling_regions",
            items = setOf(
                ResourceItem(setOf(), "default", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr"), RegionQualifier("FR")), "fr-FR", -1, -1),
                ResourceItem(setOf(LanguageQualifier("fr"), RegionQualifier("BE")), "fr-BE", -1, -1),
            )
        )
        assertEquals(
            "fr-BE",
            resourceSiblingRegions.getResourceItemByEnvironment(
                multiLocaleEnv(LocaleQualifiers("fr", "", "CA"))
            ).path
        )
    }

    @Test
    fun testSelectResourceLocalesPrefersOsListWhenComposeHasOne() {
        val compose = listOf(LocaleQualifiers("en", "", "US"))
        val system = listOf(
            LocaleQualifiers("zh", "Hans", "CN"),
            LocaleQualifiers("en", "", "US"),
        )
        assertEquals(system, selectResourceLocales(compose, system))
    }

    @Test
    fun testSelectResourceLocalesKeepsComposeWhenItAlreadyHasFallbacks() {
        val compose = listOf(
            LocaleQualifiers("de", "", "DE"),
            LocaleQualifiers("en", "", "US"),
        )
        val system = listOf(
            LocaleQualifiers("fr", "", "FR"),
            LocaleQualifiers("es", "", "ES"),
        )
        assertEquals(compose, selectResourceLocales(compose, system))
    }

    @Test
    fun testSelectResourceLocalesPrependsComposeOverride() {
        val compose = listOf(LocaleQualifiers("de", "", "DE"))
        val system = listOf(
            LocaleQualifiers("fr", "", "FR"),
            LocaleQualifiers("es", "", "ES"),
        )
        assertEquals(listOf(compose.single()) + system, selectResourceLocales(compose, system))
    }

    @Test
    fun testSelectResourceLocalesUsesSystemWhenComposeIsEmpty() {
        val system = listOf(
            LocaleQualifiers("hi", "", "IN"),
            LocaleQualifiers("en", "", "US"),
        )
        assertEquals(system, selectResourceLocales(emptyList(), system))
    }
}
