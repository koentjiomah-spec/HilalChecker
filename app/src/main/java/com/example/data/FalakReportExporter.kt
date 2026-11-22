package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.domain.falak.FalakReportDocument
import com.example.domain.falak.FalakReportGenerator
import com.example.domain.falak.FalakReportInput
import com.example.domain.falak.VerdictStatus
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object FalakReportExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points (72 dpi)
    private const val MARGIN_H = 36f
    private const val MARGIN_BOTTOM = 44f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_H * 2

    fun writeTextToOutputStream(input: FalakReportInput, outputStream: OutputStream) {
        val text = FalakReportGenerator.buildTextReport(input)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(text)
            writer.flush()
        }
    }

    fun writePdfToOutputStream(input: FalakReportInput, outputStream: OutputStream) {
        val docModel = FalakReportGenerator.buildStructuredReport(input)
        val pdf = PdfDocument()
        try {
            renderDocumentToPdf(pdf, docModel)
            pdf.writeTo(outputStream)
            outputStream.flush()
        } finally {
            pdf.close()
        }
    }

    fun createShareablePdfUri(context: Context, input: FalakReportInput): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        // Keep cache clean by removing older temporary PDF files
        reportsDir.listFiles()?.forEach { oldFile ->
            if (oldFile.isFile && System.currentTimeMillis() - oldFile.lastModified() > 3_600_000L) {
                oldFile.delete()
            }
        }
        val baseName = FalakReportGenerator.suggestedFileName(input)
        val outFile = File(reportsDir, "$baseName.pdf")
        FileOutputStream(outFile).use { fos ->
            writePdfToOutputStream(input, fos)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    fun createShareableTextUri(context: Context, input: FalakReportInput): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val baseName = FalakReportGenerator.suggestedFileName(input)
        val outFile = File(reportsDir, "$baseName.txt")
        FileOutputStream(outFile).use { fos ->
            writeTextToOutputStream(input, fos)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    fun launchSharePdfIntent(context: Context, pdfUri: Uri, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Bagikan Laporan PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun launchShareTextIntent(context: Context, textReport: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, textReport)
        }
        val chooser = Intent.createChooser(intent, "Bagikan Laporan Teks").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun renderDocumentToPdf(pdf: PdfDocument, model: FalakReportDocument) {
        val headerBgPaint = Paint().apply { color = Color.rgb(12, 22, 49) }
        val goldPaint = Paint().apply {
            color = Color.rgb(212, 160, 44)
            strokeWidth = 2f
        }
        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(214, 224, 245)
            textSize = 9.5f
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 65, 81)
            textSize = 8.5f
        }
        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 22, 49)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 102, 122)
            textSize = 8.5f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            textSize = 9f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val zebraPaint = Paint().apply { color = Color.rgb(244, 247, 252) }
        val dividerPaint = Paint().apply {
            color = Color.rgb(218, 226, 238)
            strokeWidth = 0.7f
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 130, 145)
            textSize = 8f
        }

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        fun drawFooter(pNum: Int) {
            canvas.drawLine(MARGIN_H, PAGE_HEIGHT - 28f, PAGE_WIDTH - MARGIN_H, PAGE_HEIGHT - 28f, dividerPaint)
            canvas.drawText(
                "Hilal Checker • ${model.generatedAtLabel}",
                MARGIN_H,
                PAGE_HEIGHT - 16f,
                footerPaint
            )
            val pageStr = "Halaman $pNum"
            val tw = footerPaint.measureText(pageStr)
            canvas.drawText(pageStr, PAGE_WIDTH - MARGIN_H - tw, PAGE_HEIGHT - 16f, footerPaint)
        }

        fun nextPage(): Float {
            drawFooter(pageNumber)
            pdf.finishPage(page)
            pageNumber += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            return 40f
        }

        // First page banner
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 84f, headerBgPaint)
        canvas.drawLine(0f, 84f, PAGE_WIDTH.toFloat(), 84f, goldPaint)
        canvas.drawText(model.title, MARGIN_H, 34f, headerTitlePaint)
        canvas.drawText(model.subtitle, MARGIN_H, 52f, headerSubPaint)
        canvas.drawText("Dibuat: ${model.generatedAtLabel} • ${model.scope.label}", MARGIN_H, 68f, headerSubPaint)

        var y = 102f
        val markazLines = wrapText(model.markazSummary, metaPaint, CONTENT_WIDTH)
        markazLines.forEach { line ->
            canvas.drawText(line, MARGIN_H, y, metaPaint)
            y += 12f
        }
        y += 6f

        val labelColWidth = CONTENT_WIDTH * 0.44f
        val valueColX = MARGIN_H + labelColWidth + 8f
        val valueColWidth = CONTENT_WIDTH - labelColWidth - 8f

        model.sections.forEach { section ->
            val headerNeeded = 28f +
                (if (section.subtitle != null) 14f else 0f) +
                (if (section.highlightText != null) 24f else 0f)
            if (y + headerNeeded > PAGE_HEIGHT - MARGIN_BOTTOM) {
                y = nextPage()
            }

            // Section Title
            canvas.drawText(section.title, MARGIN_H, y, sectionTitlePaint)
            y += 13f
            if (!section.subtitle.isNullOrBlank()) {
                wrapText(section.subtitle, sectionSubPaint, CONTENT_WIDTH).forEach { line ->
                    canvas.drawText(line, MARGIN_H, y, sectionSubPaint)
                    y += 11f
                }
            }
            canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, goldPaint)
            y += 8f

            // Highlight badge if present
            if (!section.highlightText.isNullOrBlank()) {
                val (bgColor, textColor) = when (section.highlightStatus) {
                    VerdictStatus.MEMENUHI -> Color.rgb(220, 252, 231) to Color.rgb(22, 101, 52)
                    VerdictStatus.MARGINAL -> Color.rgb(254, 243, 199) to Color.rgb(146, 64, 14)
                    VerdictStatus.TIDAK_MEMENUHI -> Color.rgb(254, 226, 226) to Color.rgb(153, 27, 27)
                    null -> Color.rgb(239, 246, 255) to Color.rgb(30, 64, 175)
                }
                val badgeBg = Paint().apply { color = bgColor }
                val badgeTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val badgeLines = wrapText(section.highlightText, badgeTxt, CONTENT_WIDTH - 16f)
                val badgeHeight = 10f + badgeLines.size * 12f
                if (y + badgeHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    y = nextPage()
                }
                canvas.drawRoundRect(
                    RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + badgeHeight),
                    6f,
                    6f,
                    badgeBg
                )
                var by = y + 14f
                badgeLines.forEach { line ->
                    canvas.drawText(line, MARGIN_H + 8f, by, badgeTxt)
                    by += 12f
                }
                y += badgeHeight + 6f
            }

            // Rows
            section.rows.forEachIndexed { idx, row ->
                val lLines = wrapText(row.label, labelPaint, labelColWidth)
                val vLines = wrapText(row.value, valuePaint, valueColWidth)
                val lineCount = maxOf(lLines.size, vLines.size)
                val rowHeight = lineCount * 12f + 6f

                if (y + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    y = nextPage()
                }

                if (idx % 2 == 0) {
                    canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowHeight, zebraPaint)
                }

                var ly = y + 11f
                lLines.forEach { line ->
                    canvas.drawText(line, MARGIN_H + 6f, ly, labelPaint)
                    ly += 12f
                }
                var vy = y + 11f
                vLines.forEach { line ->
                    canvas.drawText(line, valueColX, vy, valuePaint)
                    vy += 12f
                }

                y += rowHeight
                canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, dividerPaint)
            }

            if (!section.note.isNullOrBlank()) {
                y += 10f
                val noteLines = wrapText("Catatan: ${section.note}", notePaint, CONTENT_WIDTH)
                noteLines.forEach { line ->
                    if (y + 12f > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        y = nextPage()
                    }
                    canvas.drawText(line, MARGIN_H, y, notePaint)
                    y += 11f
                }
            }
            y += 14f
        }

        drawFooter(pageNumber)
        pdf.finishPage(page)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                }
                if (paint.measureText(word) <= maxWidth) {
                    current = StringBuilder(word)
                } else {
                    // Hard split very long tokens
                    var chunk = StringBuilder()
                    for (ch in word) {
                        val c = "$chunk$ch"
                        if (paint.measureText(c) <= maxWidth) {
                            chunk.append(ch)
                        } else {
                            if (chunk.isNotEmpty()) lines += chunk.toString()
                            chunk = StringBuilder(ch.toString())
                        }
                    }
                    current = chunk
                }
            }
        }
        if (current.isNotEmpty()) {
            lines += current.toString()
        }
        return lines
    }
}
