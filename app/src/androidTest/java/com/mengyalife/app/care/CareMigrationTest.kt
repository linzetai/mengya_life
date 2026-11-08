package com.mengyalife.app.care

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mengyalife.app.data.local.MengyaDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), MengyaDatabase::class.java,
    )

    @Test
    fun migrate21To22AddsNotifiedAt() {
        helper.createDatabase("migration-care", 21).apply {
            execSQL(
                "INSERT INTO care_decisions(opportunity_id,action,reason,source_refs,model_ok,created_at) " +
                    "VALUES(1,'SEND','r','s',1,1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            "migration-care", 22, true, MengyaDatabase.MIGRATION_21_22,
        )
        db.query("SELECT notified_at FROM care_decisions WHERE opportunity_id=1").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(true, c.isNull(0))
        }
        db.close()
    }
}
