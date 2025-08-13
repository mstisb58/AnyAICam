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
import java.io.IOException

class PreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var croppedBitmap: Bitmap
    private var tempImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tempImagePath = intent.getStringExtra(EXTRA_CROPPED_IMAGE_PATH)
        if (tempImagePath == null) {
            Toast.makeText(this, "画像のパスが見つかりません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 修正: BitmapFactory.decodeFileがnullを返す可能性に対応
        val decodedBitmap = BitmapFactory.decodeFile(tempImagePath)
        if (decodedBitmap == null) {
            Toast.makeText(this, "画像のデコードに失敗しました", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        croppedBitmap = decodedBitmap
        binding.previewImage.setImageBitmap(croppedBitmap)


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

        if (uri == null) {
            Toast.makeText(this, "画像の保存場所を作成できませんでした", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 修正: より安全な保存処理
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) {
                    throw IOException("Failed to get output stream.")
                }
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            Toast.makeText(this, "画像を保存しました", Toast.LENGTH_SHORT).show()
            finish() // 保存が成功したら画面を閉じる
        } catch (e: Exception) {
            // 保存に失敗した場合、作成した空のURIを削除してクリーンアップ
            resolver.delete(uri, null, null)
            Toast.makeText(this, "画像の保存に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 画面が閉じられるときに一時ファイルを削除して、ストレージをクリーンアップする
        tempImagePath?.let {
            try {
                // 修正: if文を使わずに直接削除を試みる
                // delete()はファイルが存在しない場合でも例外をスローせず、falseを返すだけなので安全
                val file = File(it)
                file.delete()
            } catch (e: Exception) {
                // セキュリティ例外など、予期せぬ例外をキャッチ
                Log.e("PreviewActivity", "一時ファイルの削除中に例外が発生しました", e)
            }
        }
    }

    companion object {
        const val EXTRA_CROPPED_IMAGE_PATH = "cropped_image_path"
    }
}
