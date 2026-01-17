package com.example.mindflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface // 确保只保留这一个 ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    suspend fun compressAndSaveImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 旋转修正
            val rotatedBitmap = rotateBitmapIfRequired(context, originalBitmap, uri)

            // 计算压缩比例 (限制最大边长 1024px)
            val maxDimension = 1024
            val scale = min(1f, maxDimension.toFloat() / max(rotatedBitmap.width, rotatedBitmap.height))

            val newWidth = (rotatedBitmap.width * scale).toInt()
            val newHeight = (rotatedBitmap.height * scale).toInt()

            val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true)

            // 创建文件
            val directory = File(context.filesDir, "compressed_images")
            if (!directory.exists()) directory.mkdirs()

            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)

            // 压缩为 JPEG (80% 质量)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.flush()
            outputStream.close()

            if (originalBitmap != scaledBitmap) originalBitmap.recycle()
            scaledBitmap.recycle()

            return@withContext file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            // 这里使用的是 androidx.exifinterface.media.ExifInterface
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            inputStream.close()

            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}