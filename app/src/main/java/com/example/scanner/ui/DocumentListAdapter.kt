package com.example.scanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scanner.R
import com.example.scanner.databinding.ItemDocumentBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.model.ScannedDocument
import java.text.DateFormat
import java.util.Date

class DocumentListAdapter(
    private val onClick: (ScannedDocument) -> Unit,
    private val onLongClick: (ScannedDocument) -> Unit,
) : RecyclerView.Adapter<DocumentListAdapter.Holder>() {

    private val items = mutableListOf<ScannedDocument>()

    fun submit(docs: List<ScannedDocument>) {
        items.clear()
        items.addAll(docs)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(
        private val binding: ItemDocumentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(doc: ScannedDocument) {
            val context = binding.root.context
            val typeLabel = when {
                doc.identityType != null -> doc.identityType.defaultTitleLabel()
                doc.type == DocumentType.ID -> context.getString(R.string.type_id)
                doc.type == DocumentType.BUSINESS_CARD -> context.getString(R.string.type_business_card)
                else -> context.getString(R.string.type_document)
            }
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(doc.createdAt))

            binding.textTitle.text = doc.title
            val pages = context.getString(R.string.pages_meta, doc.pageCount)
            binding.textMeta.text = "$typeLabel · $pages · $date"
            binding.root.setOnClickListener { onClick(doc) }
            binding.root.setOnLongClickListener {
                onLongClick(doc)
                true
            }
        }
    }
}
