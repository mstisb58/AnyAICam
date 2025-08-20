// ProcessorSelectionDialogFragment.kt
package com.example.MPdetector

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.MPdetector.databinding.DialogProcessorSelectionBinding
import com.example.MPdetector.databinding.ListItemProcessorBinding
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
        holder.binding.processorCheckbox.isChecked = selectedStatus[processor] ?: false

        holder.binding.processorCheckbox.setOnCheckedChangeListener { _, isChecked ->
            selectedStatus[processor] = isChecked
        }

        holder.binding.buttonUp.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        holder.binding.buttonDown.visibility = if (position < displayList.size - 1) View.VISIBLE else View.INVISIBLE

        holder.binding.buttonUp.setOnClickListener {
            val fromPosition = holder.adapterPosition
            if (fromPosition > 0) {
                val toPosition = fromPosition - 1
                Collections.swap(displayList, fromPosition, toPosition)
                // ### 修正箇所 ###
                // 2つのアイテムが変更されたことを通知し、再描画させる
                notifyItemRangeChanged(toPosition, 2)
            }
        }

        holder.binding.buttonDown.setOnClickListener {
            val fromPosition = holder.adapterPosition
            if (fromPosition < displayList.size - 1) {
                val toPosition = fromPosition + 1
                Collections.swap(displayList, fromPosition, toPosition)
                // ### 修正箇所 ###
                // 2つのアイテムが変更されたことを通知し、再描画させる
                notifyItemRangeChanged(fromPosition, 2)
            }
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun getSelectedProcessors(): List<ImgProcessor> {
        return displayList.filter { selectedStatus[it] == true }
    }
}
