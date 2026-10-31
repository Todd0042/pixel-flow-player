package com.pixelflow.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaybackStatsDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pixelflow_playback_stats.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_NAME = "playback_stats"
        private const val COL_URI = "uri"
        private const val COL_BUCKET_ID = "bucket_id"
        private const val COL_VIEW_COUNT = "view_count"
        private const val COL_SKIP_COUNT = "skip_count"
        private const val COL_LAST_VIEWED_AT = "last_viewed_at"
        private const val COL_LAST_SKIPPED_AT = "last_skipped_at"

        private const val TABLE_FAVORITES = "user_favorites"
        private const val COL_FAV_URI = "uri"
        private const val COL_IS_FAVORITE = "is_favorite"
        private const val COL_FAV_UPDATED_AT = "updated_at"

        @Volatile
        private var instance: PlaybackStatsDatabase? = null

        fun getInstance(context: Context): PlaybackStatsDatabase {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_NAME (
                $COL_URI TEXT PRIMARY KEY,
                $COL_BUCKET_ID TEXT NOT NULL,
                $COL_VIEW_COUNT INTEGER DEFAULT 0,
                $COL_SKIP_COUNT INTEGER DEFAULT 0,
                $COL_LAST_VIEWED_AT INTEGER DEFAULT 0,
                $COL_LAST_SKIPPED_AT INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableSql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bucket_id ON $TABLE_NAME ($COL_BUCKET_ID)")

        val createFavoritesSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                $COL_FAV_URI TEXT PRIMARY KEY,
                $COL_IS_FAVORITE INTEGER NOT NULL,
                $COL_FAV_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createFavoritesSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createFavoritesSql = """
                CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                    $COL_FAV_URI TEXT PRIMARY KEY,
                    $COL_IS_FAVORITE INTEGER NOT NULL,
                    $COL_FAV_UPDATED_AT INTEGER NOT NULL
                )
            """.trimIndent()
            db.execSQL(createFavoritesSql)
        }
    }

    suspend fun recordView(uri: Uri, bucketId: String) = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        val now = System.currentTimeMillis()
        try {
            val db = writableDatabase
            val sql = """
                INSERT INTO $TABLE_NAME ($COL_URI, $COL_BUCKET_ID, $COL_VIEW_COUNT, $COL_LAST_VIEWED_AT)
                VALUES (?, ?, 1, ?)
                ON CONFLICT($COL_URI) DO UPDATE SET
                    $COL_VIEW_COUNT = $COL_VIEW_COUNT + 1,
                    $COL_LAST_VIEWED_AT = excluded.$COL_LAST_VIEWED_AT,
                    $COL_BUCKET_ID = excluded.$COL_BUCKET_ID
            """.trimIndent()
            db.execSQL(sql, arrayOf(uriStr, bucketId, now))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun recordSkip(uri: Uri, bucketId: String) = withContext(Dispatchers.IO) {
        val uriStr = uri.toString()
        val now = System.currentTimeMillis()
        try {
            val db = writableDatabase
            val sql = """
                INSERT INTO $TABLE_NAME ($COL_URI, $COL_BUCKET_ID, $COL_SKIP_COUNT, $COL_LAST_SKIPPED_AT)
                VALUES (?, ?, 1, ?)
                ON CONFLICT($COL_URI) DO UPDATE SET
                    $COL_SKIP_COUNT = $COL_SKIP_COUNT + 1,
                    $COL_LAST_SKIPPED_AT = excluded.$COL_LAST_SKIPPED_AT,
                    $COL_BUCKET_ID = excluded.$COL_BUCKET_ID
            """.trimIndent()
            db.execSQL(sql, arrayOf(uriStr, bucketId, now))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getViewedUriStrings(): Set<String> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT $COL_URI FROM $TABLE_NAME WHERE $COL_VIEW_COUNT > 0",
                null
            ).use { cursor ->
                val colIdx = cursor.getColumnIndexOrThrow(COL_URI)
                while (cursor.moveToNext()) {
                    result.add(cursor.getString(colIdx))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun getSkippedCounts(minSkips: Int = 5): Map<String, Int> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, Int>()
        try {
            val db = readableDatabase
            db.rawQuery(
                "SELECT $COL_URI, $COL_SKIP_COUNT FROM $TABLE_NAME WHERE $COL_SKIP_COUNT >= ?",
                arrayOf(minSkips.toString())
            ).use { cursor ->
                val uriCol = cursor.getColumnIndexOrThrow(COL_URI)
                val skipCol = cursor.getColumnIndexOrThrow(COL_SKIP_COUNT)
                while (cursor.moveToNext()) {
                    result[cursor.getString(uriCol)] = cursor.getInt(skipCol)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun deleteStats(uris: List<Uri>) = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                for (uri in uris) {
                    db.delete(TABLE_NAME, "$COL_URI = ?", arrayOf(uri.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearHistory(bucketId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            if (bucketId != null) {
                db.delete(TABLE_NAME, "$COL_BUCKET_ID = ?", arrayOf(bucketId))
            } else {
                db.delete(TABLE_NAME, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setFavoriteSync(uri: Uri, isFavorite: Boolean) {
        val uriStr = uri.toString()
        val now = System.currentTimeMillis()
        try {
            val db = writableDatabase
            val sql = """
                INSERT INTO $TABLE_FAVORITES ($COL_FAV_URI, $COL_IS_FAVORITE, $COL_FAV_UPDATED_AT)
                VALUES (?, ?, ?)
                ON CONFLICT($COL_FAV_URI) DO UPDATE SET
                    $COL_IS_FAVORITE = excluded.$COL_IS_FAVORITE,
                    $COL_FAV_UPDATED_AT = excluded.$COL_FAV_UPDATED_AT
            """.trimIndent()
            db.execSQL(sql, arrayOf(uriStr, if (isFavorite) 1 else 0, now))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setFavorite(uri: Uri, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        setFavoriteSync(uri, isFavorite)
    }

    fun getFavoritesMapSync(): Map<String, Boolean> {
        val map = mutableMapOf<String, Boolean>()
        try {
            val db = readableDatabase
            db.rawQuery("SELECT $COL_FAV_URI, $COL_IS_FAVORITE FROM $TABLE_FAVORITES", null).use { cursor ->
                val uriCol = cursor.getColumnIndexOrThrow(COL_FAV_URI)
                val favCol = cursor.getColumnIndexOrThrow(COL_IS_FAVORITE)
                while (cursor.moveToNext()) {
                    map[cursor.getString(uriCol)] = cursor.getInt(favCol) == 1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    suspend fun getFavoritesMap(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        getFavoritesMapSync()
    }

    fun getFavoriteSync(uriStr: String): Boolean? {
        return try {
            val db = readableDatabase
            db.rawQuery("SELECT $COL_IS_FAVORITE FROM $TABLE_FAVORITES WHERE $COL_FAV_URI = ?", arrayOf(uriStr)).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getInt(0) == 1
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
