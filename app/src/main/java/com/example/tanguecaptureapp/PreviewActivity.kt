package com.example.tanguecaptureapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tanguecaptureapp.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var croppedBitmap: Bitmap
    private var tempImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intentからバイト配列ではなく、一時ファイルのパスを受け取る
        tempImagePath = intent.getStringExtra(EXTRA_CROPPED_IMAGE_PATH)
        if (tempImagePath != null) {
            // パスからBitmapを読み込む
            croppedBitmap = BitmapFactory.decodeFile(tempImagePath)
            binding.previewImage.setImageBitmap(croppedBitmap)
        } else {
            Toast.makeText(this, "画像の取得に失敗しました", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.resetButton.setOnClickListener {
            finish()
        }

        binding.saveButton.setOnClickListener {
            val fileName = binding.fileNameInput.text.toString()
            if (fileName.isBlank()) {
                binding.fileNameInputLayout.error = "名前を入力してください"
            } else {
                binding.fileNameInputLayout.error = null
                saveImageToGallery(croppedBitmap, fileName)
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.PNG")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TangueCaptureApp")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        Toast.makeText(this, "画像を保存しました", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "画像の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "画像の保存に失敗しました", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 画面が閉じられるときに一時ファイルを削除して、ストレージをクリーンアップする
        tempImagePath?.let {
            try {
                val file = File(it)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("PreviewActivity", "一時ファイルの削除に失敗しました", e)
            }
        }
    }

    companion object {
        // 渡すデータのキーをパス用に変更
        const val EXTRA_CROPPED_IMAGE_PATH = "cropped_image_path"
    }
}
