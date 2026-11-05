package com.ledger.app

import com.ledger.app.util.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private fun release(tag: String, assets: String, name: String = "v1.3", body: String = "\"新增应用内自动更新\"") = """
        {"tag_name":"$tag","name":"$name","body":$body,"draft":false,"prerelease":false,
         "assets":[$assets]}
    """.trimIndent()

    private val apkAsset = """{"name":"ledger.apk","size":11223344,"content_type":"application/vnd.android-package-archive",
        "browser_download_url":"https://github.com/wbw5780/ledger-app/releases/download/4/ledger.apk"}"""
    private val otherAsset = """{"name":"ledger-debug.apk","size":99,"browser_download_url":"https://example.com/x.apk"}"""

    @Test fun parsesGithubLatestRelease() {
        val r = UpdateChecker.parseRelease(release("4", "$otherAsset,$apkAsset"))
        assertNotNull(r); r!!
        assertEquals(4L, r.versionCode)
        assertEquals("1.3", r.versionName)
        assertEquals("新增应用内自动更新", r.changelog)
        assertEquals(11223344L, r.apkSize)
        assertEquals("https://github.com/wbw5780/ledger-app/releases/download/4/ledger.apk", r.apkUrl)
    }

    @Test fun missingApkAssetOrNonNumericTagIsNull() {
        assertNull(UpdateChecker.parseRelease(release("4", otherAsset)))
        assertNull(UpdateChecker.parseRelease(release("4", "")))
        assertNull(UpdateChecker.parseRelease(release("latest", apkAsset)))
    }

    @Test fun nullBodyAndNameWithoutV() {
        val r = UpdateChecker.parseRelease(release("5", apkAsset, name = "1.4", body = "null"))!!
        assertEquals("1.4", r.versionName)
        assertEquals("", r.changelog)
    }

    @Test fun onlyStrictlyGreaterTagIsNewer() {
        val r = UpdateChecker.parseRelease(release("4", apkAsset))!!
        assertTrue(UpdateChecker.isNewer(r, 3))
        assertFalse(UpdateChecker.isNewer(r, 4))
        assertFalse(UpdateChecker.isNewer(r, 5))
    }

    @Test fun megabytes() {
        assertEquals("10.7", UpdateChecker.mb(11223344))
        assertEquals("0.0", UpdateChecker.mb(0))
    }

    @Test fun constants() {
        assertEquals("https://api.github.com/repos/wbw5780/ledger-app/releases/latest", UpdateChecker.REPO_API_LATEST)
        assertEquals("ledger.apk", UpdateChecker.ASSET_NAME)
    }
}
