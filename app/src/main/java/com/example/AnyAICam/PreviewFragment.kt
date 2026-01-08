// PreviewFragment.kt
package com.example.AnyAICam

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.AnyAICam.databinding.FragmentPreviewBinding
import com.example.AnyAICam.databinding.PreviewItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var previewAdapter: PreviewAdapter

    private val processedBitmaps = mutableMapOf<ImgProcessor, Bitmap>()
    private var rawBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupUIListeners()
        generatePreviews()
    }

    private fun setupRecyclerView() {
        val displayItems = mutableListOf<Pair<String, Bitmap>>()
        previewAdapter = PreviewAdapter(displayItems)
        binding.previewRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.previewRecyclerView.adapter = previewAdapter
    }

    private fun setupUIListeners() {
        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.navigateBackToCamera()
        }
        binding.saveButton.setOnClickListener {
            openSavePopup()
        }
    }

    private fun generatePreviews() {
        lifecycleScope.launch(Dispatchers.Default) {
            rawBitmap = sharedViewModel.rawFrame.value
            if (rawBitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: No frame to process.", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.navigateBackToCamera()
                }
                return@launch
            }

            val activeProcessors = sharedViewModel.activeProcessors.value
            processedBitmaps.clear() // For saving
            val displayList = mutableListOf<Pair<String, Bitmap>>() // For preview

            if (!activeProcessors.isNullOrEmpty()) {
                activeProcessors.forEach { processor ->
                    // 1. Always generate the real bitmap for saving purposes
                    val bitmapToSave = processor.processFrameForSaving(rawBitmap!!.copy(rawBitmap!!.config, true))
                    processedBitmaps[processor] = bitmapToSave

                    // 2. Determine the bitmap for the preview display
                    val bitmapForPreview = bitmapToSave
                    displayList.add(processor.name to bitmapForPreview)
                }
            }

            withContext(Dispatchers.Main) {
                previewAdapter.updateData(displayList)
            }
        }
    }

    private fun openSavePopup() {
        val editText = EditText(requireContext()).apply {
            hint = "ファイル名（空欄の場合はタイムスタンプ）"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("名前を付けて画像を保存")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val filenameBase = editText.text.toString()
                saveImages(filenameBase)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun saveImages(filenameBase: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val finalFilenameBase = if (filenameBase.isNotBlank()) filenameBase else "capture_${timestamp}"

            var saveCount = 0
            var csvCount = 0

            processedBitmaps.forEach { (processor, bitmap) ->
                Log.d("PreviewFragment", "Saving bitmap for ${processor.name}")
                saveBitmap(bitmap, finalFilenameBase, processor.name, processor.saveDirectoryName)
                saveCount++

                // New CSV report saving logic
                val csvData = processor.getReportCsv()
                Log.d("PreviewFragment", "CSV data for ${processor.name}: ${if (csvData == null) "null" else if (csvData.isBlank()) "blank" else "length ${csvData.length}"}")
                
                csvData?.let { data ->
                    if (data.isNotBlank()) {
                        val csvFilename = "${finalFilenameBase}_${processor.name}_report.csv"
                        val success = saveCsv(csvFilename, processor.saveDirectoryName, listOf(data))
                        if (success) csvCount++
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val message = if (csvCount > 0) {
                    "$saveCount 件の画像と $csvCount 件のCSVを「Documents/AnyAICam」に保存しました。"
                } else {
                    "$saveCount 件の画像のみ保存しました（CSVはデータがないためスキップされました）。"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                (activity as? MainActivity)?.navigateBackToCamera()
            }
        }
    }

    private fun saveCsv(filename: String, directory: String, data: List<String>): Boolean {
        val resolver = requireActivity().contentResolver

        // MediaStore.Files is more generic and often more compatible for CSVs
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Documents/AnyAICam is the unified location
                val relativePath = Environment.DIRECTORY_DOCUMENTS + File.separator + "AnyAICam" + File.separator + directory
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        return try {
            val uri = resolver.insert(collection, contentValues)
            if (uri == null) {
                Log.e("PreviewFragment", "Failed to insert CSV record into MediaStore")
                return false
            }
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    data.forEach { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            
            // Force media scan so it shows up in file managers immediately
            android.media.MediaScannerConnection.scanFile(requireContext(), arrayOf(uri.toString()), null, null)
            
            Log.d("PreviewFragment", "Successfully saved CSV at: $uri")
            true
        } catch (e: Exception) {
            Log.e("PreviewFragment", "Error saving CSV", e)
            false
        }
    }

    private fun saveBitmap(bitmap: Bitmap, filenameBase: String, suffix: String, directory: String) {
        val filename = "${filenameBase}_${suffix}.png"
        val resolver = requireActivity().contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = Environment.DIRECTORY_PICTURES + File.separator + "AnyAICam" + File.separator + directory
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        try {
            val uri = resolver.insert(collection, contentValues)
            if (uri == null) {
                Log.e("PreviewFragment", "Failed to insert Image record into MediaStore")
                return
            }
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            
            // Force media scan
            android.media.MediaScannerConnection.scanFile(requireContext(), arrayOf(uri.toString()), null, null)
            
            Log.d("PreviewFragment", "Successfully saved Image at: $uri")
        } catch (e: Exception) {
            Log.e("PreviewFragment", "Error saving Image", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PreviewAdapter(private var imageList: List<Pair<String, Bitmap>>) : RecyclerView.Adapter<PreviewAdapter.PreviewViewHolder>() {

    class PreviewViewHolder(val binding: PreviewItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        val binding = PreviewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PreviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val (name, bitmap) = imageList[position]
        holder.binding.detectorName.text = name
        holder.binding.previewImage.setImageBitmap(bitmap)
    }

    override fun getItemCount() = imageList.size

    fun updateData(newImageList: List<Pair<String, Bitmap>>) {
        this.imageList = newImageList
        notifyDataSetChanged()
    }
}
