package com.ledger.app.csv

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/** 用 pdfbox-android 提取 PDF 文字（仅文字，不渲染） */
object PdfTextExtractor {

    class PdfException(message: String) : Exception(message)

    private fun load(bytes: ByteArray): PDDocument {
        val doc = try {
            PDDocument.load(bytes)
        } catch (e: InvalidPasswordException) {
            throw PdfException("PDF 已加密（需要打开密码），请先用其他工具去除密码后再导入")
        }
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
        return doc
    }

    /** 逐页纯文本（旧启发式解析的输入） */
    fun extractPages(bytes: ByteArray): List<String> = load(bytes).use { d ->
        val stripper = PDFTextStripper().apply {
            sortByPosition = true          // 按坐标排序，避免表格单元格乱序
            lineSeparator = "\n"
            wordSeparator = " "
            addMoreFormatting = false
        }
        (1..d.numberOfPages).map { p ->
            stripper.startPage = p
            stripper.endPage = p
            runCatching { stripper.getText(d) }.getOrDefault("")
        }
    }

    /** 逐页带坐标的词（[PdfTableParser] 的输入）。坐标为 PDFBox 的 DirAdj 坐标：原点左上，y 向下 */
    fun extractWords(bytes: ByteArray): List<List<PdfWord>> = load(bytes).use { d ->
        (1..d.numberOfPages).map { p ->
            val s = WordStripper()
            s.startPage = p
            s.endPage = p
            runCatching { s.getText(d); s.words.toList() }.getOrDefault(emptyList())
        }
    }

    /** 重写 writeString，收集每个词（按空白再切分）及其坐标 */
    private class WordStripper : PDFTextStripper() {
        val words = ArrayList<PdfWord>()

        init {
            sortByPosition = true
            lineSeparator = "\n"
            wordSeparator = " "
            addMoreFormatting = false
        }

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val tps = textPositions ?: return
            val buf = ArrayList<TextPosition>()
            fun flush() {
                if (buf.isEmpty()) return
                val str = buf.joinToString("") { it.unicode ?: "" }.trim()
                if (str.isNotEmpty()) {
                    val first = buf.first()
                    val last = buf.last()
                    words.add(
                        PdfWord(
                            text = str,
                            x = first.xDirAdj,
                            endX = last.xDirAdj + last.widthDirAdj,
                            y = buf.maxOf { it.yDirAdj },          // 基线
                            height = buf.maxOf { it.heightDir }.coerceAtLeast(1f),
                        ),
                    )
                }
                buf.clear()
            }
            for (tp in tps) {
                val u = tp.unicode ?: ""
                if (u.isBlank()) flush() else buf.add(tp)
            }
            flush()
        }
    }
}

/** PDF 页面上的一个词：x/endX 为左右边界，y 为基线（向下增大），height 为字高 */
data class PdfWord(val text: String, val x: Float, val endX: Float, val y: Float, val height: Float)
