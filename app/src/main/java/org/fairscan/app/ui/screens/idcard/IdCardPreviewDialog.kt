package org.fairscan.app.ui.screens.idcard

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object A4Page {
    const val WIDTH_MM = 210f
    const val HEIGHT_MM = 297f
    const val DPI = 300f
    val WIDTH_PX = (WIDTH_MM * DPI / 25.4f).toInt()
    val HEIGHT_PX = (HEIGHT_MM * DPI / 25.4f).toInt()

    fun mmToPx(mm: Float): Float = (mm * DPI) / 25.4f
}

object IdCardLayoutEngine {
    const val CARD_WIDTH_MM = 85.60f
    const val CARD_HEIGHT_MM = 53.98f

    fun createA4Sheet(front: Bitmap, back: Bitmap? = null): Bitmap {
        val pageWidth = A4Page.WIDTH_PX
        val pageHeight = A4Page.HEIGHT_PX

        val result = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.WHITE)

        val cardWidth = A4Page.mmToPx(CARD_WIDTH_MM)
        val cardHeight = A4Page.mmToPx(CARD_HEIGHT_MM)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.LTGRAY
        }

        val topMargin = A4Page.mmToPx(24f)
        val centerX = (pageWidth - cardWidth) / 2f

        val frontDest = RectF(centerX, topMargin, centerX + cardWidth, topMargin + cardHeight)
        canvas.drawBitmap(front, null, frontDest, paint)
        canvas.drawRect(frontDest, strokePaint)

        if (back != null) {
            val gapPx = A4Page.mmToPx(14f)
            val backY = topMargin + cardHeight + gapPx
            val backDest = RectF(centerX, backY, centerX + cardWidth, backY + cardHeight)
            canvas.drawBitmap(back, null, backDest, paint)
            canvas.drawRect(backDest, strokePaint)
        }

        return result
    }

    fun createMultiPageSheets(images: List<Bitmap>): List<Bitmap> {
        return images.chunked(2).map { pair ->
            createA4Sheet(pair[0], pair.getOrNull(1))
        }
    }
}

@Composable
fun IdCardPreviewDialog(
    bitmaps: List<Bitmap>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var a4Sheets by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isGenerating by remember { mutableStateOf(true) }

    LaunchedEffect(bitmaps) {
        isGenerating = true
        withContext(Dispatchers.Default) {
            a4Sheets = IdCardLayoutEngine.createMultiPageSheets(bitmaps)
        }
        isGenerating = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ID Card A4 Preview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${bitmaps.size} image(s) • ${a4Sheets.size} sheet(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Assembling A4 Sheets...")
                        }
                    } else if (a4Sheets.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { a4Sheets.size })

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                                pageSpacing = 16.dp
                            ) { pageIndex ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .aspectRatio(210f / 297f),
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Image(
                                        bitmap = a4Sheets[pageIndex].asImageBitmap(),
                                        contentDescription = "A4 Page ${pageIndex + 1}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.White),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "Page ${pagerState.currentPage + 1} of ${a4Sheets.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    val currentBmp = a4Sheets[pagerState.currentPage]
                                    scope.launch {
                                        val uri = saveIdCardPageToDownloads(
                                            context,
                                            currentBmp,
                                            pagerState.currentPage + 1
                                        )
                                        Toast.makeText(
                                            context,
                                            if (uri != null) "Page ${pagerState.currentPage + 1} saved to Downloads" else "Failed to save",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = "Download")
                                }

                                IconButton(onClick = {
                                    val currentBmp = a4Sheets[pagerState.currentPage]
                                    scope.launch {
                                        val file = saveIdCardTempFile(context, currentBmp)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            setPackage("com.noco.print")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            intent.setPackage("com.nokoprint")
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "NokoPrint app not installed",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Print, contentDescription = "Print")
                                }

                                IconButton(onClick = {
                                    val currentBmp = a4Sheets[pagerState.currentPage]
                                    scope.launch {
                                        val file = saveIdCardTempFile(context, currentBmp)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share A4 Page")
                                        )
                                    }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun saveIdCardPageToDownloads(context: Context, bitmap: Bitmap, pageNumber: Int): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "ID_Card_A4_Page${pageNumber}_$timestamp.jpg"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

suspend fun saveIdCardTempFile(context: Context, bitmap: Bitmap): File =
    withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "id_card_page_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        file
    }
