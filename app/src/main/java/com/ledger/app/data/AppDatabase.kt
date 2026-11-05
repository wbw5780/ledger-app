package com.ledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Project::class, Record::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun recordDao(): RecordDao

    /** 批量导入，返回 (新增, 跳过重复) */
    suspend fun importRecords(records: List<Record>): Pair<Int, Int> = withTransaction {
        var added = 0
        var skipped = 0
        val dao = recordDao()
        for (r in records) {
            if (dao.insertIgnore(r) > 0) added++ else skipped++
        }
        added to skipped
    }

    /** 删除项目及其全部记录（外键 CASCADE 之外再显式删除一次，双保险） */
    suspend fun deleteProject(p: Project) = withTransaction {
        recordDao().deleteByProject(p.id)
        projectDao().delete(p)
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ledger.db"
            ).addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            }).build().also { instance = it }
        }
    }
}
