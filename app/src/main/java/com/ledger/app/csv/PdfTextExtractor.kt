package com.ledger.app.csv

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

/** 用 pdfbox-android 逐页提取文本（仅文本，不渲染） */
object PdfTextExtractor {

    class PdfException(message: String) : Exception(message)

    fun extractPages(bytes: ByteArray): List<String> {
        val doc = try {
            PDDocument.load(bytes)
        } catch (e: InvalidPasswordException) {
            throw PdfException("PDF 已加密（需要打开密码），请先用其他工具去除密码后再导入")
        }
        return doc.use { d ->
            if (d.isEncrypted) d.isAllSecurityToBeRemoved = true
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
    }
}
