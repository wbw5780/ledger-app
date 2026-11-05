package com.ledger.app.ui

import com.ledger.app.csv.ParseResult

sealed interface Screen {
    data object Home : Screen
    data class ProjectDetail(val projectId: Long) : Screen
    data class ProjectEdit(val projectId: Long?) : Screen
    data class RecordEdit(val recordId: Long?, val projectId: Long?) : Screen
    data class ImportPreview(val projectId: Long, val fileName: String, val result: ParseResult) : Screen
}

class Navigator(private val stack: MutableList<Screen>) {
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    /** 返回首页（例如删除项目后） */
    fun popToHome() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}
