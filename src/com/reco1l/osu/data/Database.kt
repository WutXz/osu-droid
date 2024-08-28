package com.reco1l.osu.data

import android.content.Context
import android.util.Log
import androidx.room.*
import ru.nsu.ccfit.zuev.osu.BeatmapProperties
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.helper.sql.DBOpenHelper
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream


// Ported from rimu! project

/**
 * The osu!droid database manager.
 */
object DatabaseManager {

    /**
     * Get the beatmaps table DAO.
     */
    @JvmStatic
    val beatmapInfoTable
        get() = database.getBeatmapInfoTable()

    /**
     * Get the beatmap options table DAO.
     */
    @JvmStatic
    val beatmapOptionsTable
        get() = database.getBeatmapOptionsTable()

    /**
     * Get the score table DAO.
     */
    @JvmStatic
    val scoreInfoTable
        get() = database.getScoreInfoTable()


    private lateinit var database: DroidDatabase


    @JvmStatic
    fun load(context: Context) {

        // Be careful when changing the database name, it may cause data loss.
        database = Room.databaseBuilder(context, DroidDatabase::class.java, DroidDatabase::class.simpleName)
            // Is preferable to support migrations, otherwise destructive migration will run forcing
            // tables to recreate (in case of beatmaps table it'll re-import all beatmaps).
            // See https://developer.android.com/training/data-storage/room/migrating-db-versions.
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()

        loadLegacyMigrations(context)
    }

    @Suppress("UNCHECKED_CAST")
    fun loadLegacyMigrations(context: Context) {

        // BeatmapOptions
        try {
            val oldPropertiesFile = File(context.filesDir, "properties")

            if (oldPropertiesFile.exists()) {

                GlobalManager.getInstance().info = "Migrating beatmap properties..."

                oldPropertiesFile.inputStream().use { fis ->

                    ObjectInputStream(fis).use { ois ->

                        // Ignoring first object which is intended to be the version.
                        ois.readObject()

                        val options = (ois.readObject() as Map<String, BeatmapProperties>).map { (path, props) ->

                            BeatmapOptions(path, props.isFavorite, props.offset)
                        }

                        beatmapOptionsTable.addAll(options)
                    }


                }
            }

        } catch (e: IOException) {
            Log.e("DatabaseManager", "Failed to migrate legacy beatmap properties", e)
        }

        // Score table
        try {
            val oldDatabaseFile = File(Config.getCorePath(), "databases/osudroid_test.db")

            if (oldDatabaseFile.exists()) {

                GlobalManager.getInstance().info = "Migrating score table..."

                DBOpenHelper.getOrCreate(context).use { helper ->

                    helper.writableDatabase.use { db ->

                        db.rawQuery("SELECT * FROM scores", null).use {

                            var pendingScores = it.count

                            while (it.moveToNext()) {

                                try {
                                    val scoreInfo = ScoreInfo(
                                        it.getInt(it.getColumnIndexOrThrow("id")).toLong(),
                                        it.getString(it.getColumnIndexOrThrow("filename")),
                                        it.getString(it.getColumnIndexOrThrow("playername")),
                                        it.getString(it.getColumnIndexOrThrow("replayfile")),
                                        it.getString(it.getColumnIndexOrThrow("mode")),
                                        it.getInt(it.getColumnIndexOrThrow("score")),
                                        it.getInt(it.getColumnIndexOrThrow("combo")),
                                        it.getString(it.getColumnIndexOrThrow("mark")),
                                        it.getInt(it.getColumnIndexOrThrow("h300k")),
                                        it.getInt(it.getColumnIndexOrThrow("h300")),
                                        it.getInt(it.getColumnIndexOrThrow("h100k")),
                                        it.getInt(it.getColumnIndexOrThrow("h100")),
                                        it.getInt(it.getColumnIndexOrThrow("h50")),
                                        it.getInt(it.getColumnIndexOrThrow("misses")),
                                        it.getFloat(it.getColumnIndexOrThrow("accuracy")),
                                        it.getLong(it.getColumnIndexOrThrow("time")),
                                        it.getInt(it.getColumnIndexOrThrow("perfect")) == 1
                                    )

                                    scoreInfoTable.insertScore(scoreInfo)
                                    db.rawQuery("DELETE FROM scores WHERE id = ${scoreInfo.id}", null)
                                    pendingScores--

                                } catch (e: Exception) {
                                    Log.e("ScoreLibrary", "Failed to import score from old database.", e)
                                }
                            }

                            if (pendingScores <= 0) {
                                oldDatabaseFile.renameTo(File(Config.getCorePath(), "databases/osudroid_test.olddb"))
                            }
                        }
                    }

                }

            }

        } catch (e: IOException) {
            Log.e("DatabaseManager", "Failed to migrate legacy score table", e)
        }

    }

}

@Database(
    version = 1,
    entities = [
        BeatmapInfo::class,
        BeatmapOptions::class,
        ScoreInfo::class
    ]
)
abstract class DroidDatabase : RoomDatabase() {

    abstract fun getBeatmapInfoTable(): IBeatmapInfoDAO

    abstract fun getBeatmapOptionsTable(): IBeatmapOptionsDAO

    abstract fun getScoreInfoTable(): IScoreInfoDAO

}