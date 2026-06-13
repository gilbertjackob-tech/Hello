package com.glassbox.hello.database

import android.content.Context
import com.glassbox.hello.debug.AppLog as Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glassbox.hello.database.dao.CacheDao
import com.glassbox.hello.database.dao.CookieDao
import com.glassbox.hello.database.dao.DownloadDao
import com.glassbox.hello.database.dao.HistoryDao
import com.glassbox.hello.database.dao.ProfileDao
import com.glassbox.hello.database.entities.CacheEntity
import com.glassbox.hello.database.entities.CookieEntity
import com.glassbox.hello.database.entities.DownloadEntity
import com.glassbox.hello.database.entities.HistoryEntity
import com.glassbox.hello.database.entities.ProfileEntity

/**
 * Browser Room database for profile-isolated state.
 */
@Database(
    entities = [
        ProfileEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        CacheEntity::class,
        CookieEntity::class
    ],
    version = AppDatabase.DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * Returns the profile DAO.
     */
    abstract fun profileDao(): ProfileDao

    /**
     * Returns the history DAO.
     */
    abstract fun historyDao(): HistoryDao

    /**
     * Returns the download DAO.
     */
    abstract fun downloadDao(): DownloadDao

    /**
     * Returns the cache DAO.
     */
    abstract fun cacheDao(): CacheDao

    /**
     * Returns the cookie DAO.
     */
    abstract fun cookieDao(): CookieDao

    companion object {
        const val DATABASE_VERSION: Int = 1
        const val DATABASE_NAME: String = "browser_database"

        @Volatile
        private var instance: AppDatabase? = null

        private val migrations: Array<Migration> = emptyArray()

        /**
         * Returns the singleton database instance using the application context.
         */
        fun getInstance(context: Context): AppDatabase {
            val applicationContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*migrations)
                    .addCallback(databaseCallback)
                    .build()
                    .also { database -> instance = database }
            }
        }

        /**
         * Closes and clears the singleton instance for tests and controlled shutdown.
         */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        /**
         * Returns the configured migration list for tests.
         */
        fun migrationList(): List<Migration> {
            return migrations.toList()
        }

        private val databaseCallback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Log.d(TAG, "Browser database opened.")
            }
        }

        private const val TAG: String = "AppDatabase"
    }
}
