package com.example.scanner.ui

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.example.scanner.R
import com.example.scanner.databinding.DialogIdLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Visual chooser for ID Letter placement. Does not touch camera/crop.
 */
object IdLayoutChooser {

    fun show(
        activity: AppCompatActivity,
        onChosen: (sideBySide: Boolean) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity)
        val binding = DialogIdLayoutBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        var sideBySide = false

        fun render() {
            binding.optionStacked.setBackgroundResource(
                if (!sideBySide) R.drawable.bg_id_option_selected else R.drawable.bg_id_option_unselected
            )
            binding.optionSide.setBackgroundResource(
                if (sideBySide) R.drawable.bg_id_option_selected else R.drawable.bg_id_option_unselected
            )
            binding.checkStacked.setImageResource(
                if (!sideBySide) R.drawable.ic_check_selected else R.drawable.ic_radio_unselected
            )
            binding.checkSide.setImageResource(
                if (sideBySide) R.drawable.ic_check_selected else R.drawable.ic_radio_unselected
            )
        }

        binding.optionStacked.setOnClickListener {
            sideBySide = false
            render()
        }
        binding.optionSide.setOnClickListener {
            sideBySide = true
            render()
        }
        binding.buttonCancel.setOnClickListener { dialog.dismiss() }
        binding.buttonContinue.setOnClickListener {
            dialog.dismiss()
            onChosen(sideBySide)
        }

        render()
        dialog.show()
    }
}
