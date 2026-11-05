package com.mengyalife.app.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-12/S-02 的静态检查：Manifest 与 dataExtractionRules 必须排除系统备份。 */
class BackupManifestSecurityTest {
    private fun projectFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 $path（test working dir=${File(".").absolutePath}）")
    }

    @Test
    fun `manifest keeps backup disabled and declares extraction rules`() {
        val text = projectFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(text.contains("android:allowBackup=\"false\""))
        assertTrue(text.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(text.contains("android:fullBackupContent=\"false\""))
    }

    @Test
    fun `extraction rules exclude cloud and device transfer domains`() {
        val text = projectFile("app/src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(text.contains("<cloud-backup>"))
        assertTrue(text.contains("<device-transfer>"))
        for (domain in listOf("root", "file", "database", "sharedpref", "external")) {
            assertTrue("missing domain $domain", text.contains("domain=\"$domain\""))
        }
    }
}
