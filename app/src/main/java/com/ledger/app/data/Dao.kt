package com.ledger.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY created_at ASC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Insert
    suspend fun insert(p: Project): Long

    @Update
    suspend fun update(p: Project)

    @Delete
    suspend fun delete(p: Project)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE project_id = :projectId ORDER BY timestamp DESC, id DESC")
    fun observeByProject(projectId: Long): Flow<List<Record>>

    @Query("SELECT * FROM records ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<Record>>

    @Query(
        """SELECT project_id AS projectId,
           COALESCE(SUM(CASE WHEN type = 1 THEN amount_cents ELSE 0 END), 0) AS income,
           COALESCE(SUM(CASE WHEN type = 2 THEN amount_cents ELSE 0 END), 0) AS expense
           FROM records GROUP BY project_id"""
    )
    fun observeSummaries(): Flow<List<ProjectSummary>>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): Record?

    @Insert
    suspend fun insert(r: Record): Long

    /** 冲突（来源+source_id 重复）时返回 -1 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(r: Record): Long

    @Update
    suspend fun update(r: Record)

    @Delete
    suspend fun delete(r: Record)

    @Query("DELETE FROM records WHERE project_id = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
