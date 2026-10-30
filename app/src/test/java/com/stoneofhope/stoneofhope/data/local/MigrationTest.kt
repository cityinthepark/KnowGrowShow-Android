package com.stoneofhope.stoneofhope.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a database exactly as the exported `schemas/…/<n>.json` describes, then lets Room open it
 * at the current version: Room runs the migrations and fails if the result doesn't match the
 * entities. (MigrationTestHelper reads schemas from test assets, which Robolectric doesn't see.)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    private fun createFromSchema(version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(
            File("schemas/com.stoneofhope.stoneofhope.data.local.AppDatabase/$version.json").readText(),
        ).jsonObject.getValue("database").jsonObject
        val statements = buildList {
            for (entity in schema.getValue("entities").jsonArray.map { it.jsonObject }) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                add(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach {
                    add(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["views"]?.jsonArray?.forEach {
                val view = it.jsonObject
                add(view.getValue("createSql").jsonPrimitive.content.replace("\${VIEW_NAME}", view.getValue("viewName").jsonPrimitive.content))
            }
            schema.getValue("setupQueries").jsonArray.forEach { add(it.jsonPrimitive.content) }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { seed(it.writableDatabase) }
    }

    @Test
    fun from1KeepsDataAndAddsOnlineChurches() = runBlocking {
        createFromSchema(1) { db ->
            db.execSQL("INSERT INTO tags (id, name, createdAt, needsSync) VALUES ('6a166ac5f315e27a81e5b126', 'Faith', 1, 0)")
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            assertEquals("Faith", db.tagDao().getById("6a166ac5f315e27a81e5b126")?.name)
            assertTrue(db.onlineChurchDao().active().isEmpty())
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
