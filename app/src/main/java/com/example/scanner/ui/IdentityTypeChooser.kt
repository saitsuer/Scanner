package com.example.scanner.ui

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.example.scanner.R
import com.example.scanner.databinding.DialogIdentityTypeBinding
import com.example.scanner.model.IdentityType
import com.google.android.material.bottomsheet.BottomSheetDialog

object IdentityTypeChooser {

    fun show(
        activity: AppCompatActivity,
        onChosen: (IdentityType) -> Unit,
    ) {
        val dialog = BottomSheetDialog(activity)
        val binding = DialogIdentityTypeBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        var selected = IdentityType.DRIVERS_LICENSE

        fun render() {
            fun bg(selectedNow: Boolean) =
                if (selectedNow) R.drawable.bg_id_option_selected else R.drawable.bg_id_option_unselected
            binding.optionDriversLicense.setBackgroundResource(bg(selected == IdentityType.DRIVERS_LICENSE))
            binding.optionStateId.setBackgroundResource(bg(selected == IdentityType.STATE_ID))
            binding.optionPassport.setBackgroundResource(bg(selected == IdentityType.PASSPORT))
            binding.optionOtherCard.setBackgroundResource(bg(selected == IdentityType.OTHER_CARD))
        }

        binding.optionDriversLicense.setOnClickListener {
            selected = IdentityType.DRIVERS_LICENSE
            render()
        }
        binding.optionStateId.setOnClickListener {
            selected = IdentityType.STATE_ID
            render()
        }
        binding.optionPassport.setOnClickListener {
            selected = IdentityType.PASSPORT
            render()
        }
        binding.optionOtherCard.setOnClickListener {
            selected = IdentityType.OTHER_CARD
            render()
        }
        binding.buttonCancel.setOnClickListener { dialog.dismiss() }
        binding.buttonContinue.setOnClickListener {
            dialog.dismiss()
            onChosen(selected)
        }

        render()
        dialog.show()
    }
}
