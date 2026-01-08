// ProcessorSelectionDialogFragment.kt
package com.example.AnyAICam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.AnyAICam.databinding.DialogProcessorSelectionBinding
import com.example.AnyAICam.databinding.ListItemProcessorBinding
import com.example.AnyAICam.models.tongue_detector.ImgAnalyzer
import java.util.Collections

interface ProcessorSelectionListener {
    fun onProcessorsSelected(selectedProcessors: List<ImgProcessor>)
}

class ProcessorSelectionDialogFragment(
    private val allProcessors: List<ImgProcessor>,
    private val initiallySelected: List<ImgProcessor>,
    private val listener: ProcessorSelectionListener
) : DialogFragment() {

    private var _binding: DialogProcessorSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogProcessorSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ProcessorAdapter(allProcessors.toMutableList(), initiallySelected)
        binding.processorRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.processorRecyclerView.adapter = adapter

        binding.confirmButton.setOnClickListener {
            listener.onProcessorsSelected(adapter.getSelectedProcessors())
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
            it.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ProcessorAdapter(
    private val displayList: MutableList<ImgProcessor>,
    initiallySelected: List<ImgProcessor>
) : RecyclerView.Adapter<ProcessorAdapter.ProcessorViewHolder>() {

    private val selectedStatus = displayList.associateWith { initiallySelected.contains(it) }.toMutableMap()

    inner class ProcessorViewHolder(val binding: ListItemProcessorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessorViewHolder {
        val binding = ListItemProcessorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProcessorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProcessorViewHolder, position: Int) {
        val processor = displayList[position]
        holder.binding.processorName.text = processor.name

        val isSelected = selectedStatus[processor] ?: false

        // To prevent the listener from firing during re-binding, set it to null first.
        holder.binding.processorCheckbox.setOnCheckedChangeListener(null)
        holder.binding.processorCheckbox.isChecked = isSelected

        // Handle expanding/collapsing the options view
        if (isSelected) {
            holder.binding.itemOptionsContainer.visibility = View.VISIBLE
            populateOptions(holder.binding.itemOptionsContainer, processor)
        } else {
            holder.binding.itemOptionsContainer.visibility = View.GONE
            holder.binding.itemOptionsContainer.removeAllViews()
        }

        // Now, set the listener for user interactions.
        holder.binding.processorCheckbox.setOnCheckedChangeListener { _, isChecked ->
            selectedStatus[processor] = isChecked
            // Redraw the item to show/hide the options container
            notifyItemChanged(position)
        }

        // --- Reorder buttons logic (unchanged) ---
        holder.binding.buttonUp.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        holder.binding.buttonDown.visibility = if (position < displayList.size - 1) View.VISIBLE else View.INVISIBLE

        holder.binding.buttonUp.setOnClickListener {
            val fromPosition = holder.adapterPosition
            if (fromPosition > 0) {
                val toPosition = fromPosition - 1
                Collections.swap(displayList, fromPosition, toPosition)
                notifyItemMoved(fromPosition, toPosition)
            }
        }

        holder.binding.buttonDown.setOnClickListener {
            val fromPosition = holder.adapterPosition
            if (fromPosition < displayList.size - 1) {
                val toPosition = fromPosition + 1
                Collections.swap(displayList, fromPosition, toPosition)
                notifyItemMoved(fromPosition, toPosition)
            }
        }
    }

    private fun populateOptions(container: LinearLayout, processor: ImgProcessor) {
        container.removeAllViews()
        val context = container.context

        // Add model-specific options
        if (processor is com.example.AnyAICam.models.face_detector.ImgAnalyzer) {
            val saveLandmarksCheckBox = CheckBox(context).apply {
                text = "ランドマークを保存"
                isChecked = processor.isSaveLandmarksEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    processor.isSaveLandmarksEnabled = isChecked
                }
            }
            container.addView(saveLandmarksCheckBox)
        }

        if (processor is com.example.AnyAICam.models.pose_detector.ImgAnalyzer) {
            val saveLandmarksCheckBox = CheckBox(context).apply {
                text = "ランドマークを保存"
                isChecked = processor.isSaveLandmarksEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    processor.isSaveLandmarksEnabled = isChecked
                }
            }
            container.addView(saveLandmarksCheckBox)
        }

        if (processor is com.example.AnyAICam.models.show_aqua.ImgAnalyzer) {
            val radioGroup = RadioGroup(context).apply {
                orientation = RadioGroup.VERTICAL
            }

            val reportButton = RadioButton(context).apply {
                text = "解析レポート"
                id = View.generateViewId()
                isChecked = processor.operatingMode == com.example.AnyAICam.models.show_aqua.ImgAnalyzer.OperatingMode.REPORT
            }

            val heatmapButton = RadioButton(context).apply {
                text = "ヒートマップ表示"
                id = View.generateViewId()
                isChecked = processor.operatingMode == com.example.AnyAICam.models.show_aqua.ImgAnalyzer.OperatingMode.HEATMAP
            }

            radioGroup.addView(reportButton)
            radioGroup.addView(heatmapButton)

            radioGroup.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    reportButton.id -> processor.operatingMode = com.example.AnyAICam.models.show_aqua.ImgAnalyzer.OperatingMode.REPORT
                    heatmapButton.id -> processor.operatingMode = com.example.AnyAICam.models.show_aqua.ImgAnalyzer.OperatingMode.HEATMAP
                }
            }
            container.addView(radioGroup)

            // Add CSV export option for show_aqua
            val csvCheckBox = CheckBox(context).apply {
                text = "CSV結果を書き出す"
                isChecked = processor.isCsvExportEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    processor.isCsvExportEnabled = isChecked
                }
            }
            container.addView(csvCheckBox)
        }

        if (processor is com.example.AnyAICam.models.tongue_detector.ImgAnalyzer) { // This is the Tongue Detector
            val forceShutterCheckBox = CheckBox(context).apply {
                text = "Always Enable Shutter"
                isChecked = processor.forceShutterEnabled
                setOnCheckedChangeListener { _, isChecked ->
                    processor.forceShutterEnabled = isChecked
                }
            }
            container.addView(forceShutterCheckBox)
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun getSelectedProcessors(): List<ImgProcessor> {
        return displayList.filter { selectedStatus[it] == true }
    }
}
