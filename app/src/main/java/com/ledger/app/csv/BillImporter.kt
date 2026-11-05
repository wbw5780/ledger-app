package com.ledger.app.csv

/**
 * 导入总入口：按文件头魔数路由，不看扩展名。
 *  - `%PDF-` → PDF 文本提取 + [PdfBillParser]
 *  - `PK\u0003\u0004`（zip / xlsx）→ 友好提示
 *  - 其他 → 原 CSV 流程 [BillParser.parse]（BOM / 严格 UTF-8 / GBK / UTF-16 自动识别）
 */
object BillImporter {

    fun parse(
        bytes: ByteArray,
        fileName: String? = null,
        pdfPages: (ByteArray) -> List<String> = PdfTextExtractor::extractPages,
    ): ParseResult {
        if (PdfBillParser.isPdf(bytes)) return parsePdf(bytes, pdfPages)
        if (isZip(bytes)) {
            return ParseResult(
                null, emptyList(), 0, "ZIP",
                "这是 zip 压缩包（或 xlsx 表格）。请先解压（解压密码见微信支付/支付宝通知），再选择其中的 csv 或 pdf 文件",
            )
        }
        return BillParser.parse(bytes, fileName)
    }

    private fun parsePdf(bytes: ByteArray, pdfPages: (ByteArray) -> List<String>): ParseResult {
        val pages = try {
            pdfPages(bytes)
        } catch (e: PdfTextExtractor.PdfException) {
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, e.message)
        } catch (e: Throwable) { // 包括 OOM / NoClassDefFoundError 等，一律不崩
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, "PDF 读取失败：${e.message ?: e.javaClass.simpleName}")
        }
        return try {
            PdfBillParser.parsePages(pages)
        } catch (e: Throwable) {
            ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, "PDF 解析失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()
}
