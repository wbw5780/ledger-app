package com.ledger.app.csv

/**
 * 导入总入口：按文件头魔数路由，不看扩展名。
 *  - `%PDF-` → 带坐标提取 + [PdfTableParser]（按表头分列，主路径）；找不到表头时退回纯文本 + [PdfBillParser]（旧启发式兜底）
 *  - `PK\u0003\u0004`（zip / xlsx）→ 友好提示
 *  - 其他 → 原 CSV 流程 [BillParser.parse]（BOM / 严格 UTF-8 / GBK / UTF-16 自动识别）
 */
object BillImporter {

    fun parse(
        bytes: ByteArray,
        fileName: String? = null,
        pdfPages: (ByteArray) -> List<String> = PdfTextExtractor::extractPages,
        pdfWords: (ByteArray) -> List<List<PdfWord>> = PdfTextExtractor::extractWords,
    ): ParseResult {
        if (PdfBillParser.isPdf(bytes)) return parsePdf(bytes, pdfPages, pdfWords)
        if (isZip(bytes)) {
            return ParseResult(
                null, emptyList(), 0, "ZIP",
                "这是 zip 压缩包（或 xlsx 表格）。请先解压（解压密码见微信支付/支付宝通知），再选择其中的 csv 或 pdf 文件",
            )
        }
        return BillParser.parse(bytes, fileName)
    }

    private fun parsePdf(
        bytes: ByteArray,
        pdfPages: (ByteArray) -> List<String>,
        pdfWords: (ByteArray) -> List<List<PdfWord>>,
    ): ParseResult {
        // 1. 主路径：按坐标分列的表格解析
        try {
            val words = pdfWords(bytes)
            PdfTableParser.parse(words)?.let { return it }
        } catch (e: PdfTextExtractor.PdfException) {
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, e.message)
        } catch (_: Throwable) {
            // 坐标提取失败：继续走兜底
        }
        // 2. 兜底：找不到表头（其他版式）→ 旧的纯文本启发式
        val pages = try {
            pdfPages(bytes)
        } catch (e: PdfTextExtractor.PdfException) {
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, e.message)
        } catch (e: Throwable) { // 包括 OOM / NoClassDefFoundError 等，一律不崩
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, "PDF 读取失败：${e.message ?: e.javaClass.simpleName}")
        }
        val r = try {
            PdfBillParser.parsePages(pages)
        } catch (e: Throwable) {
            return ParseResult(null, emptyList(), 0, PdfBillParser.ENCODING_LABEL, "PDF 解析失败：${e.message ?: e.javaClass.simpleName}")
        }
        // 识别不出就明确报错，而不是导入错数据
        if (r.rows.isEmpty()) {
            return ParseResult(null, emptyList(), r.skippedLines, PdfBillParser.ENCODING_LABEL,
                "无法识别的账单格式：" + (r.error ?: "PDF 中没有可识别的收入/支出交易"))
        }
        return r
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()
}
