// file: app/src/main/java/com/example/MPdetector/PreviewActivity.kt
package com.example.MPdetector

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// Rクラスのimport文を追加して、IDを確実に見つけられるようにします
import com.example.MPdetector.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PreviewActivity : AppCompatActivity() {

    private lateinit var previewImage: ImageView
    private lateinit var fileNameInput: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: Button
    private var croppedImage: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        // クラッシュの原因箇所。XMLファイルとIDが一致していることを確認します。
        previewImage = findViewById(R.id.preview_image)
        fileNameInput = findViewById(R.id.file_name_input)
        saveButton = findViewById(R.id.save_button)
        backButton = findViewById(R.id.back_button)

        val imagePath = intent.getStringExtra("image_path")
        if (imagePath != null) {
            val file = File(imagePath)
            if (file.exists()) {
                croppedImage = BitmapFactory.decodeFile(file.absolutePath)
                previewImage.setImageBitmap(croppedImage)
                file.delete() // 一時ファイルを削除
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        fileNameInput.setText("IMG_$timeStamp")

        saveButton.setOnClickListener {
            croppedImage?.let {
                saveImageToGallery(it, fileNameInput.text.toString())
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, displayName: String) {
        if (displayName.isBlank()) {
            Toast.makeText(this, "ファイル名を入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { targetUri ->
                resolver.openOutputStream(targetUri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                        throw IOException("Failed to save bitmap.")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(targetUri, values, null, null)
                }
                Toast.makeText(this, "画像を保存しました", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        } catch (e: IOException) {
            uri?.let { resolver.delete(it, null, null) }
            Toast.makeText(this, "保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}