package com.scrollmeter.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The single local database, in app-private storage. Nothing ever leaves the phone. */
@Database(
    entities = [SessionEntity::class, HourlyBucketEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scrollDao(): ScrollDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "scrollmeter.db")
                .build()
    }
}
