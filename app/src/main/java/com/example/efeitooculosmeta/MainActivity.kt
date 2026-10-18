package com.example.efeitooculosmeta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.efeitooculosmeta.ui.theme.EfeitoOculosMetaTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val OUTPUT_WIDTH = 3024
private const val OUTPUT_HEIGHT = 4032

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EfeitoOculosMetaTheme { ConverterScreen() } }
    }

    private fun createOutput(uri: Uri, visualEffect: Boolean): File {
        val source = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            ?: error("Não foi possível abrir a imagem")
        val converted = source.centerCropAndResize()
        if (visualEffect) converted.addSmartGlassesBadge()
        val directory = File(cacheDir, "images").apply { mkdirs() }
        val output = File(directory, "meta_format_${System.currentTimeMillis()}.jpg")
        output.outputStream().use { converted.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        converted.recycle()
        source.recycle()
        val timestamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
        ExifInterface(output.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Meta AI")
            setAttribute(ExifInterface.TAG_MODEL, "Ray-Ban Meta Smart Glasses 2")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            // EXIF ColorSpace 1 = sRGB. This constant is not exposed by every
            // AndroidX ExifInterface version.
            setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
            setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Meta AI")
            setAttribute(ExifInterface.TAG_SOFTWARE, "Meta View 202.0.0")
            setAttribute(ExifInterface.TAG_F_NUMBER, "22/10")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "224/100")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/17")
            setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "3943")
            setAttribute(ExifInterface.TAG_COPYRIGHT, "Meta AI")
            setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, UUID.randomUUID().toString())
            setAttribute(ExifInterface.TAG_X_RESOLUTION, "72/1")
            setAttribute(ExifInterface.TAG_Y_RESOLUTION, "72/1")
            setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, ExifInterface.RESOLUTION_UNIT_INCHES.toString())
            setAttribute(ExifInterface.TAG_DATETIME, timestamp)
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timestamp)
            setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, timestamp)
            saveAttributes()
        }
        return output
    }

    private fun share(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar imagem"))
    }

    private fun shareToInstagramStory(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage("com.instagram.android")
            type = "image/jpeg"
            putExtra("interactive_asset_uri", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Instagram não está instalado ou não aceita Stories neste dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    private fun ConverterScreen() {
        var selectedUri by remember { mutableStateOf<Uri?>(null) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        var output by remember { mutableStateOf<File?>(null) }
        var message by remember { mutableStateOf<String?>(null) }
        var visualEffect by remember { mutableStateOf(true) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedUri = uri
            output = null
            message = null
            preview = uri?.let { contentResolver.openInputStream(it).use { stream -> BitmapFactory.decodeStream(stream) } }
        }
        val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { destination ->
            val file = output
            if (destination != null && file != null) {
                contentResolver.openOutputStream(destination)?.use { it.write(file.readBytes()) }
                message = "Imagem salva com sucesso."
            }
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Meta Format", style = MaterialTheme.typography.headlineMedium)
                Text("Converta uma foto para o formato compatível com fotos dos óculos Ray-Ban Meta.", style = MaterialTheme.typography.bodyLarge)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "Processamento local: a foto não é enviada para nenhum servidor. O arquivo recebe metadados de formato; isso não prova que foi capturado por um óculos real.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedUri == null) "Escolher foto" else "Escolher outra foto")
                }
                preview?.let { bitmap: Bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Prévia",
                        modifier = Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = visualEffect, onCheckedChange = { visualEffect = it })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Adicionar marca visual")
                        Text("Ícone e texto de efeito na imagem", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(
                    onClick = {
                        selectedUri?.let {
                            runCatching { createOutput(it, visualEffect) }.onSuccess { file ->
                                output = file
                                message = "Convertida para 3024 × 4032, JPEG 95%."
                            }.onFailure { error -> message = error.message ?: "Falha ao converter a imagem." }
                        }
                    },
                    enabled = selectedUri != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Converter para formato Meta") }
                output?.let {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        OutlinedButton(onClick = { saver.launch("meta_format.jpg") }) { Text("Salvar") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { share(it) }) { Text("Compartilhar") }
                    }
                    Button(onClick = { shareToInstagramStory(it) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir no Instagram Stories")
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(8.dp))
                Text("Saída: 3:4 • 3024 × 4032 • JPEG • sem GPS ou metadados privados", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun Bitmap.centerCropAndResize(): Bitmap {
    val targetRatio = OUTPUT_WIDTH.toFloat() / OUTPUT_HEIGHT
    val sourceRatio = width.toFloat() / height
    val cropWidth: Int
    val cropHeight: Int
    if (sourceRatio > targetRatio) {
        cropHeight = height
        cropWidth = (height * targetRatio).toInt()
    } else {
        cropWidth = width
        cropHeight = (width / targetRatio).toInt()
    }
    val left = (width - cropWidth) / 2
    val top = (height - cropHeight) / 2
    return Bitmap.createScaledBitmap(Bitmap.createBitmap(this, left, top, cropWidth, cropHeight), OUTPUT_WIDTH, OUTPUT_HEIGHT, true)
}

private fun Bitmap.addSmartGlassesBadge() {
    val canvas = Canvas(this)
    val scale = width / 1080f
    val margin = (54 * scale).toInt()
    val badgeHeight = (112 * scale).toInt()
    val badgeTop = height - margin - badgeHeight
    val badgeRight = width - margin

    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 10, 10, 12)
    }
    canvas.drawRoundRect(
        margin.toFloat(), badgeTop.toFloat(), badgeRight.toFloat(), (height - margin).toFloat(),
        22 * scale, 22 * scale, background
    )

    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6 * scale
        strokeCap = Paint.Cap.ROUND
    }
    val centerY = badgeTop + badgeHeight / 2f
    val lensWidth = 34 * scale
    val lensHeight = 24 * scale
    val leftCenter = margin + 48 * scale
    val rightCenter = margin + 112 * scale
    canvas.drawRoundRect(leftCenter - lensWidth, centerY - lensHeight, leftCenter + lensWidth, centerY + lensHeight, 12 * scale, 12 * scale, line)
    canvas.drawRoundRect(rightCenter - lensWidth, centerY - lensHeight, rightCenter + lensWidth, centerY + lensHeight, 12 * scale, 12 * scale, line)
    canvas.drawLine(leftCenter + lensWidth, centerY, rightCenter - lensWidth, centerY, line)

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30 * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("SMART GLASSES STYLE", margin + 160 * scale, centerY + 11 * scale, text)
}
