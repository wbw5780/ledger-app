package com.ledger.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 关键词，以换行分隔存储 */
    val keywords: String = "",
    /** filesDir/icons/ 下的文件名，可为空 */
    @ColumnInfo(name = "icon_file") val iconFile: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    fun keywordList(): List<String> {
        val list = keywords.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { listOf(name.trim()).filter { it.isNotEmpty() } }
    }
}

object RecordType {
    const val INCOME = 1
    const val EXPENSE = 2
}

object RecordSource {
    const val MANUAL = "manual"
    const val WECHAT = "wechat"
    const val ALIPAY = "alipay"
}

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["source", "source_id"], unique = true),
        Index(value = ["timestamp"]),
    ]
)
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    /** RecordType.INCOME / EXPENSE */
    val type: Int,
    /** 金额，单位：分 */
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val timestamp: Long,
    /** 对方/商品名 */
    val counterparty: String = "",
    val note: String = "",
    /** manual / wechat / alipay */
    val source: String = RecordSource.MANUAL,
    /** 外部单号，手动记录为 null（SQLite 唯一索引中 NULL 互不冲突） */
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
) {
    val signedCents: Long get() = if (type == RecordType.INCOME) amountCents else -amountCents
}

data class ProjectSummary(
    val projectId: Long,
    val income: Long,
    val expense: Long,
)

data class DayNet(
    val day: Long,
    val net: Long,
)
