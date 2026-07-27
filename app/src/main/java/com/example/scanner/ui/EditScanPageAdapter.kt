package com.example.scanner.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scanner.R
import com.example.scanner.databinding.ItemEditPageBinding
import java.io.File

class EditScanPageAdapter(
    private val pages: MutableList<File>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<EditScanPageAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemEditPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(position)

    override fun getItemCount(): Int = pages.size

    inner class Holder(private val binding: ItemEditPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            val context = binding.root.context
            binding.textPageLabel.text = context.getString(R.string.page_number, position + 1)
            binding.imageThumb.setImageBitmap(BitmapFactory.decodeFile(pages[position].absolutePath))
            binding.root.setOnClickListener { onClick(position) }
        }
    }
}
