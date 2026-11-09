// Written by Mudlej. License is GPLv3.

package com.gitlab.mudlej.MjPdfReader.ui.reader.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.gitlab.mudlej.MjPdfReader.core.text.TextDirection

enum class QuoteCardTheme(
    val backgroundColor: Int,
    val textColor: Int,
    val secondaryColor: Int,
    val decorColor: Int,
) {
    LIGHT(0xFFF8F4EC.toInt(), 0xFF23262D.toInt(), 0xFF6A6E76.toInt(), 0x2823262D),
    DARK(0xFF20232A.toInt(), 0xFFECEDEF.toInt(), 0xFFA4A8B0.toInt(), 0x28ECEDEF),
    SEPIA(0xFFF0E3C9.toInt(), 0xFF4A3B28.toInt(), 0xFF87755C.toInt(), 0x284A3B28.toInt()),
    ROSE(0xFFFCE4EC.toInt(), 0xFF7A2E4E.toInt(), 0xFFB05C7E.toInt(), 0x33D81B7A),
    LAVENDER(0xFFEDE7F6.toInt(), 0xFF4A337E.toInt(), 0xFF7E6BAE.toInt(), 0x335E35B1),
    MINT(0xFFE3F4EC.toInt(), 0xFF1F5140.toInt(), 0xFF4F8574.toInt(), 0x2300796B),
}

data class QuoteCardOptions(
    val quote: String,
    val bookName: String,
    val author: String,
    val showMadeBy: Boolean,
    val theme: QuoteCardTheme,
    val reflow: Boolean,
)

object QuoteImageRenderer {

    const val MAX_QUOTE_CHARS = 400

    private const val SIZE = 1080
    private const val HORIZONTAL_MARGIN = 128f
    private const val QUOTE_AREA_TOP = 200f
    private const val QUOTE_AREA_BOTTOM = 800f
    private const val MIN_TEXT_SIZE = 38f
    private const val LINE_SPACING = 1.25f

    fun render(options: QuoteCardOptions): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val theme = options.theme
        canvas.drawColor(theme.backgroundColor)

        val rtl = TextDirection.isRtlBaseDirection(options.quote)
        drawDecorativeQuoteMark(canvas, theme, rtl)
        drawQuote(canvas, options, rtl)
        drawBookAndAuthor(canvas, options)
        if (options.showMadeBy) {
            drawMadeBy(canvas, theme)
        }
        return bitmap
    }

    private fun drawDecorativeQuoteMark(canvas: Canvas, theme: QuoteCardTheme, rtl: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.decorColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 300f
        }
        if (rtl) {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("”", SIZE - 64f, 280f, paint)
        } else {
            canvas.drawText("“", 64f, 280f, paint)
        }
    }

    private fun drawQuote(canvas: Canvas, options: QuoteCardOptions, rtl: Boolean) {
        var quote = options.quote.trim()
        if (options.reflow) {
            quote = quote.replace(Regex("\\s+"), " ")
        }
        quote = if (rtl) "”$quote“" else "“$quote”"

        val width = (SIZE - 2 * HORIZONTAL_MARGIN).toInt()
        val maxHeight = QUOTE_AREA_BOTTOM - QUOTE_AREA_TOP
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.theme.textColor
            typeface = if (rtl) Typeface.SERIF else Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        var layout: StaticLayout? = null
        var textSize = 64f
        while (textSize >= MIN_TEXT_SIZE) {
            paint.textSize = textSize
            val candidate = buildLayout(quote, paint, width, rtl)
            if (candidate.height <= maxHeight) {
                layout = candidate
                break
            }
            textSize -= 4f
        }
        if (layout == null) {
            paint.textSize = MIN_TEXT_SIZE
            val maxLines = (maxHeight / (MIN_TEXT_SIZE * LINE_SPACING)).toInt().coerceAtLeast(1)
            layout = buildLayout(quote, paint, width, rtl, maxLines = maxLines)
        }

        val top = QUOTE_AREA_TOP + ((maxHeight - layout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(HORIZONTAL_MARGIN, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int, rtl: Boolean, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(0f, LINE_SPACING)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun drawBookAndAuthor(canvas: Canvas, options: QuoteCardOptions) {
        val theme = options.theme
        val centerX = SIZE / 2f
        var baseline = 900f

        val bookName = options.bookName.trim()
        if (bookName.isNotBlank()) {
            val bookPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                textSize = 42f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(TextUtils.ellipsize(bookName, TextPaint(bookPaint), SIZE - 160f, TextUtils.TruncateAt.END).toString(), centerX, baseline, bookPaint)
            baseline += 58f
        }

        val author = options.author.trim()
        if (author.isNotBlank()) {
            val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.secondaryColor
                typeface = Typeface.SERIF
                textSize = 34f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(TextUtils.ellipsize(author, TextPaint(authorPaint), SIZE - 160f, TextUtils.TruncateAt.END).toString(), centerX, baseline, authorPaint)
        }
    }

    private fun drawMadeBy(canvas: Canvas, theme: QuoteCardTheme) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.secondaryColor
            typeface = Typeface.SANS_SERIF
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Made by MJ PDF", SIZE - 40f, SIZE - 40f, paint)
    }
}
