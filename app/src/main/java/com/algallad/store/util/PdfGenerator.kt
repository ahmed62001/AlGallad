package com.algallad.store.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.algallad.store.data.Order
import com.algallad.store.data.StoreSettings
import com.algallad.store.data.minorToPounds
import java.io.File
import java.io.FileOutputStream

/** فاتورة PDF ثنائية اللغة (عربي/إنجليزي) بتخطيط من اليمين لليسار. */
object PdfGenerator {
    private const val W = 595
    private const val H = 842
    private const val RIGHT = W - 36f
    private const val LEFT = 36f
    private const val NAVY = 0xFF0B2545.toInt()
    private const val GOLD = 0xFFC99A2E.toInt()
    private const val GREY = 0xFF5F6B7A.toInt()
    private const val ROW_BG = 0xFFF1F3F7.toInt()
    private const val CUR = "ج.م"

    private fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.RIGHT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun Canvas.txt(s: String, x: Float, y: Float, p: TextPaint, maxWidth: Float? = null) {
        val t = if (maxWidth != null) TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString() else s
        drawText(t, x, y, p)
    }

    fun create(ctx: Context, order: Order, store: StoreSettings): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c = page.canvas
        val fill = Paint().apply { style = Paint.Style.FILL }

        fun header() {
            fill.color = NAVY
            c.drawRect(0f, 0f, W.toFloat(), 96f, fill)
            c.txt(store.storeNameAr.ifBlank { "مؤسسة الجلاد للتجارة" }, RIGHT, 42f, paint(22f, 0xFFFFFFFF.toInt(), true))
            c.txt(store.storeNameEn, RIGHT, 66f, paint(12f, GOLD, true))
            c.txt("فاتورة طلب", LEFT, 42f, paint(18f, GOLD, true, Paint.Align.LEFT))
            c.txt("Order Invoice", LEFT, 64f, paint(11f, 0xFFFFFFFF.toInt(), false, Paint.Align.LEFT))
        }

        fun tableHeader(y: Float): Float {
            fill.color = NAVY
            c.drawRect(LEFT, y, RIGHT, y + 28f, fill)
            val h = paint(11f, 0xFFFFFFFF.toInt(), true)
            val hc = paint(11f, 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)
            c.txt("#", RIGHT - 6f, y + 19f, h)
            c.txt("المنتج / Product", RIGHT - 30f, y + 19f, h)
            c.txt("الكمية / Qty", 285f, y + 19f, hc)
            c.txt("السعر / Price", 205f, y + 19f, hc)
            c.txt("الإجمالي / Total", 110f, y + 19f, hc)
            return y + 28f
        }

        header()
        var y = 124f

        // بيانات الطلب والعميل
        fun info(label: String, value: String) {
            if (value.isBlank()) return
            c.txt(label, RIGHT, y, paint(11f, GREY, true))
            c.txt(value, RIGHT - 120f, y, paint(12f, 0xFF1B2430.toInt()), RIGHT - 120f - LEFT)
            y += 20f
        }
        info("رقم الطلب / Order #", "#${order.shortId()}")
        info("التاريخ / Date", fmtDate(order.createdAt))
        info("العميل / Customer", order.customerName)
        info("الهاتف / Phone", order.customerPhone)
        info("العنوان / Address", order.address)
        info("ملاحظات / Notes", order.notes)
        y += 10f
        y = tableHeader(y)

        val nameP = paint(12f, 0xFF1B2430.toInt())
        val numP = paint(12f, 0xFF1B2430.toInt(), false, Paint.Align.CENTER)
        order.items.forEachIndexed { i, it ->
            if (y > H - 150f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                header()
                y = tableHeader(120f)
            }
            if (i % 2 == 0) {
                fill.color = ROW_BG
                c.drawRect(LEFT, y, RIGHT, y + 28f, fill)
            }
            c.txt("${i + 1}", RIGHT - 6f, y + 19f, nameP)
            c.txt(it.nameAr.ifBlank { it.nameEn }, RIGHT - 30f, y + 19f, nameP, 215f)
            c.txt("${it.qty}", 285f, y + 19f, numP)
            c.txt(minorToPounds(it.unitPriceMinor), 205f, y + 19f, numP)
            c.txt(minorToPounds(it.unitPriceMinor * it.qty), 110f, y + 19f, numP)
            y += 28f
        }

        // الإجمالي
        y += 14f
        if (y > H - 110f) {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            header()
            y = 130f
        }
        fill.color = GOLD
        c.drawRoundRect(LEFT, y, RIGHT, y + 44f, 10f, 10f, fill)
        c.txt("الإجمالي / Total", RIGHT - 14f, y + 28f, paint(15f, NAVY, true))
        c.txt("${minorToPounds(order.totalMinor)} $CUR", LEFT + 14f, y + 28f, paint(17f, NAVY, true, Paint.Align.LEFT))

        c.txt("شكراً لتعاملكم معنا — Thank you for your business", W / 2f, H - 40f,
            paint(11f, GREY, false, Paint.Align.CENTER))
        doc.finishPage(page)

        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "order_${order.shortId()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
