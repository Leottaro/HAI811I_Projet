package fr.cestnous.travelwow.travelPath.util

import fr.cestnous.travelwow.R
import fr.cestnous.travelwow.BuildConfig
import fr.cestnous.travelwow.travelPath.data.*
import fr.cestnous.travelwow.travelPath.service.*
import fr.cestnous.travelwow.travelPath.ui.*
import fr.cestnous.travelwow.travelPath.ui.theme.*
import fr.cestnous.travelwow.travelPath.util.*

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40f

    suspend fun exportPostToPdf(context: Context, post: FirebasePost, author: FirebaseUser, steps: List<FirebaseStep>): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }
        val metaPaint = Paint().apply {
            textSize = 10f
            color = Color.GRAY
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var currentY = MARGIN

        // --- Header Section ---
        canvas.drawText(post.title, MARGIN, currentY + 24f, titlePaint)
        currentY += 40f

        canvas.drawText("Par ${author.username} • ${post.locationName}", MARGIN, currentY, metaPaint)
        currentY += 20f
        
        canvas.drawText("${"%.1f".format(post.distanceKm)} km", MARGIN, currentY, metaPaint)
        currentY += 30f

        // --- Description ---
        if (!post.description.isNullOrBlank()) {
            canvas.drawText("Description", MARGIN, currentY, headerPaint)
            currentY += 20f
            
            val descriptionLines = wrapText(post.description, bodyPaint, PAGE_WIDTH - 2 * MARGIN)
            for (line in descriptionLines) {
                if (currentY > PAGE_HEIGHT - MARGIN) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }
                canvas.drawText(line, MARGIN, currentY, bodyPaint)
                currentY += 15f
            }
            currentY += 20f
        }

        // --- Steps ---
        canvas.drawText("Étapes du parcours", MARGIN, currentY, headerPaint)
        currentY += 25f

        val imageLoader = ImageLoader(context)

        for ((index, step) in steps.withIndex()) {
            // Check for new page before drawing step title
            if (currentY > PAGE_HEIGHT - 100) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN
            }

            canvas.drawText("${index + 1}. ${step.name}", MARGIN, currentY, headerPaint)
            currentY += 20f

            if (step.description.isNotBlank()) {
                val stepDescLines = wrapText(step.description, bodyPaint, PAGE_WIDTH - 2 * MARGIN)
                for (line in stepDescLines) {
                    if (currentY > PAGE_HEIGHT - MARGIN) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = MARGIN
                    }
                    canvas.drawText(line, MARGIN, currentY, bodyPaint)
                    currentY += 15f
                }
            }

            // Draw Step Images
            if (step.imageUrls.isNotEmpty()) {
                currentY += 10f
                for (imageUrl in step.imageUrls) {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .allowHardware(false)
                            .build()
                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as BitmapDrawable).bitmap
                            
                            // Scale bitmap to fit
                            val maxWidth = PAGE_WIDTH - 2 * MARGIN
                            val scale = maxWidth / bitmap.width.toFloat()
                            val scaledHeight = bitmap.height * scale
                            
                            if (currentY + scaledHeight > PAGE_HEIGHT - MARGIN) {
                                pdfDocument.finishPage(page)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                                page = pdfDocument.startPage(pageInfo)
                                canvas = page.canvas
                                currentY = MARGIN
                            }

                            val destRect = RectF(MARGIN, currentY, MARGIN + maxWidth, currentY + scaledHeight)
                            canvas.drawBitmap(bitmap, null, destRect, null)
                            currentY += scaledHeight + 10f
                        }
                    } catch (e: Exception) {
                        Log.e("PdfExporter", "Error loading image: $imageUrl", e)
                    }
                }
            }
            currentY += 20f
        }

        pdfDocument.finishPage(page)

        // Save file
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TravelWow_${post.title.replace(" ", "_")}_$timeStamp.pdf"
        
        // Save to internal app files to be sure it works without external permissions
        // Then use FileProvider to share it
        val file = File(context.filesDir, fileName)

        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            Log.d("PdfExporter", "PDF successfully written to ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e("PdfExporter", "Error writing PDF to ${file.absolutePath}", e)
            pdfDocument.close()
            return null
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}
