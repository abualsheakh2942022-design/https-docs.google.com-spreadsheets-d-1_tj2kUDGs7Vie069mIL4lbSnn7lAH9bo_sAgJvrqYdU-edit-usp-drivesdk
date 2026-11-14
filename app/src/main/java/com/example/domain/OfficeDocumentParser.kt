package com.example.domain

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pure-Kotlin/JVM parser for engineering specification documents & BOQ spreadsheets:
 * - Microsoft Word (.docx): extracts paragraphs and structured tables (w:tbl -> rows separated by " | ")
 * - Microsoft Excel (.xlsx): extracts shared strings & worksheet rows formatted as readable tables
 * - Plain text / CSV / TSV: decodes UTF-8/UTF-16/Windows-1256 (Arabic ANSI fallback) safely
 */
object OfficeDocumentParser {

    fun extractTextFromDocx(inputStream: InputStream, maxChars: Int = 36_000): String {
        val xmlEntries = linkedMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.lowercase()
                    if (name == "word/document.xml" ||
                        name.startsWith("word/header") ||
                        name.startsWith("word/footer")
                    ) {
                        xmlEntries[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val docBytes = xmlEntries["word/document.xml"]
            ?: throw IllegalStateException("ملف Word (.docx) غير صالح أو لا يحتوي على مستند نصي.")

        val out = StringBuilder()

        // Include headers first if present (often contain project name / consultant / spec title)
        xmlEntries.keys
            .filter { it.startsWith("word/header") }
            .sorted()
            .forEach { headerKey ->
                xmlEntries[headerKey]?.let { bytes ->
                    val headerText = parseWordXmlToText(bytes).trim()
                    if (headerText.isNotBlank()) {
                        out.appendLine("[ترويسة المستند]").appendLine(headerText)
                    }
                }
            }

        out.append(parseWordXmlToText(docBytes))

        val result = out.toString().trim()
        if (result.isBlank()) {
            throw IllegalStateException("لم يتم العثور على نص مقروء داخل ملف Word (.docx).")
        }
        return if (result.length > maxChars) result.take(maxChars) else result
    }

    private fun parseWordXmlToText(xmlBytes: ByteArray): String {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val root = doc.documentElement
        val body = findFirstChildByLocalName(root, "body") ?: root

        val sb = StringBuilder()
        val children = body.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val local = localTagName(node)
            when (local) {
                "p" -> {
                    val line = extractWordParagraphText(node).trim()
                    if (line.isNotBlank()) {
                        sb.appendLine(line)
                    }
                }
                "tbl" -> {
                    appendWordTable(node, sb)
                    sb.appendLine()
                }
                "sdt" -> {
                    val sdtContent = findFirstChildByLocalName(node, "sdtContent")
                    if (sdtContent != null) {
                        val innerText = collectWordSubtreeText(sdtContent).trim()
                        if (innerText.isNotBlank()) sb.appendLine(innerText)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun appendWordTable(tableNode: Node, sb: StringBuilder) {
        val rows = tableNode.childNodes
        for (r in 0 until rows.length) {
            val row = rows.item(r)
            if (row.nodeType != Node.ELEMENT_NODE || localTagName(row) != "tr") continue
            val cellTexts = mutableListOf<String>()
            val cells = row.childNodes
            for (c in 0 until cells.length) {
                val cell = cells.item(c)
                if (cell.nodeType != Node.ELEMENT_NODE || localTagName(cell) != "tc") continue
                val cellValue = collectWordSubtreeText(cell)
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                cellTexts.add(cellValue)
            }
            if (cellTexts.any { it.isNotBlank() }) {
                sb.appendLine(cellTexts.joinToString(" | "))
            }
        }
    }

    private fun collectWordSubtreeText(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            when (localTagName(child)) {
                "p" -> {
                    val pText = extractWordParagraphText(child).trim()
                    if (pText.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append(" ")
                        sb.append(pText)
                    }
                }
                "tbl" -> {
                    val nested = StringBuilder()
                    appendWordTable(child, nested)
                    if (nested.isNotEmpty()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(nested)
                    }
                }
                else -> {
                    val sub = collectWordSubtreeText(child)
                    if (sub.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append(" ")
                        sb.append(sub)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun extractWordParagraphText(pNode: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            if (n.nodeType != Node.ELEMENT_NODE) return
            when (localTagName(n)) {
                "t", "delText", "instrText" -> {
                    sb.append(n.textContent ?: "")
                    return
                }
                "tab" -> {
                    sb.append("\t")
                    return
                }
                "br", "cr" -> {
                    sb.append(" ")
                    return
                }
            }
            val ch = n.childNodes
            for (i in 0 until ch.length) {
                walk(ch.item(i))
            }
        }
        walk(pNode)
        return sb.toString()
    }

    fun extractTextFromXlsx(inputStream: InputStream, maxChars: Int = 36_000): String {
        var sharedStringsBytes: ByteArray? = null
        val sheetBytesMap = linkedMapOf<String, ByteArray>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.lowercase()
                    when {
                        name == "xl/sharedstrings.xml" -> sharedStringsBytes = zip.readBytes()
                        name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                            sheetBytesMap[name] = zip.readBytes()
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (sheetBytesMap.isEmpty()) {
            throw IllegalStateException("ملف Excel (.xlsx) غير صالح أو لا يحتوي على أوراق عمل.")
        }

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it) } ?: emptyList()
        val sb = StringBuilder()

        sheetBytesMap.keys.sorted().forEachIndexed { idx, key ->
            val bytes = sheetBytesMap[key] ?: return@forEachIndexed
            val sheetContent = parseWorksheetXml(bytes, sharedStrings).trim()
            if (sheetContent.isNotBlank()) {
                sb.appendLine("=== ورقة عمل ${idx + 1} ($key) ===")
                sb.appendLine(sheetContent)
                sb.appendLine()
            }
            if (sb.length >= maxChars) return@forEachIndexed
        }

        val result = sb.toString().trim()
        if (result.isBlank()) {
            throw IllegalStateException("أوراق العمل في ملف Excel فارغة.")
        }
        return if (result.length > maxChars) result.take(maxChars) else result
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val list = mutableListOf<String>()
        val siNodes = doc.getElementsByTagName("si")
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i)
            val sb = StringBuilder()
            collectElementsByLocalName(si, "t") { el ->
                sb.append(el.textContent ?: "")
            }
            list.add(sb.toString())
        }
        return list
    }

    private fun parseWorksheetXml(bytes: ByteArray, sharedStrings: List<String>): String {
        val doc = newSafeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val sb = StringBuilder()
        val rowNodes = doc.getElementsByTagName("row")
        for (r in 0 until rowNodes.length) {
            val row = rowNodes.item(r)
            val rowCells = mutableListOf<String>()
            val children = row.childNodes
            for (c in 0 until children.length) {
                val cell = children.item(c)
                if (cell.nodeType != Node.ELEMENT_NODE || localTagName(cell) != "c") continue
                val cellEl = cell as Element
                val cellType = cellEl.getAttribute("t")
                val valueStr = when (cellType) {
                    "s" -> {
                        val vText = findFirstChildByLocalName(cellEl, "v")?.textContent?.trim()
                        val idx = vText?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        val isNode = findFirstChildByLocalName(cellEl, "is")
                        if (isNode != null) {
                            val tBuf = StringBuilder()
                            collectElementsByLocalName(isNode, "t") { tBuf.append(it.textContent ?: "") }
                            tBuf.toString()
                        } else ""
                    }
                    else -> {
                        findFirstChildByLocalName(cellEl, "v")?.textContent?.trim() ?: ""
                    }
                }.replace(Regex("""\s+"""), " ").trim()
                if (valueStr.isNotEmpty()) {
                    rowCells.add(valueStr)
                }
            }
            if (rowCells.isNotEmpty()) {
                sb.appendLine(rowCells.joinToString(" | "))
            }
        }
        return sb.toString()
    }

    /**
     * Decodes plain text / CSV / TSV bytes cleanly, handling UTF-8 BOM, UTF-16 BOM,
     * and Arabic Windows-1256 fallback if UTF-8 decoding detects invalid byte sequences.
     */
    fun decodeTextBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // UTF-8 BOM
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        // UTF-16 LE / BE BOM
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            try {
                String(bytes, Charset.forName("windows-1256"))
            } catch (_: Exception) {
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    private fun newSafeDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (_: Exception) {
            // Some minimal XML parsers may ignore optional features
        }
        isExpandEntityReferences = false
    }.newDocumentBuilder()

    private fun localTagName(node: Node): String {
        val raw = node.nodeName ?: return ""
        val colon = raw.indexOf(':')
        return if (colon >= 0 && colon + 1 < raw.length) raw.substring(colon + 1) else raw
    }

    private fun findFirstChildByLocalName(parent: Node, targetLocal: String): Node? {
        val ch = parent.childNodes
        for (i in 0 until ch.length) {
            val n = ch.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && localTagName(n) == targetLocal) {
                return n
            }
        }
        return null
    }

    private fun collectElementsByLocalName(parent: Node, targetLocal: String, onEach: (Node) -> Unit) {
        val ch = parent.childNodes
        for (i in 0 until ch.length) {
            val n = ch.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) {
                if (localTagName(n) == targetLocal) {
                    onEach(n)
                }
                collectElementsByLocalName(n, targetLocal, onEach)
            }
        }
    }
}
