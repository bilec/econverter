package com.econverter.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConverterLicensesTest {
    @Test
    fun requiredLicensesAreAvailableOffline() {
        val assets = RuntimeEnvironment.getApplication().assets
        for ((path, notice) in mapOf(
            "licenses/econverter/LICENSE" to "Version 3, 29 June 2007",
            "licenses/python/LICENSE.txt" to "PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2",
            "licenses/python/libffi.txt" to "1996-2022  Anthony Green, Red Hat",
            "licenses/python/bzip2.txt" to "bzip2/libbzip2 version 1.0.8",
            "licenses/chaquopy/LICENSE.txt" to "Chaquo Ltd and contributors",
        )) {
            val text = assets.open(path).bufferedReader().use { it.readText() }
            assertTrue("Missing notice in $path", text.contains(notice))
            assertTrue("Missing license terms in $path", text.length > 1000)
        }
    }

    @Test
    fun pythonIncorporatedSoftwareNoticesAreAvailableOffline() {
        val assets = RuntimeEnvironment.getApplication().assets
        val text = assets.open("licenses/python/INCORPORATED.txt").bufferedReader().use { it.readText() }
        for (notice in listOf("Makoto Matsumoto", "Marek Majkowski", "Lucent Technologies", "Red Hat", "Stefan Krah", "MagicStack")) {
            assertTrue("Missing Python third-party notice: $notice", text.contains(notice))
        }
    }

    @Test
    fun additionalBinaryAttributionsAreAvailableOffline() {
        val text = RuntimeEnvironment.getApplication().assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() }
        for (notice in listOf("work of the FreeType Team", "European Environment Agency")) {
            assertTrue("Missing binary attribution: $notice", text.contains(notice))
        }
    }
}
