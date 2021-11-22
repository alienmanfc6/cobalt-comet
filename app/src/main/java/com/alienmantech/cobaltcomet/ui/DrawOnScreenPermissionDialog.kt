package com.alienmantech.cobaltcomet.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.alienmantech.cobaltcomet.R

class DrawOnScreenPermissionDialog(private val listener: OnResultListener) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        return AlertDialog.Builder(requireActivity())
            .setTitle(R.string.draw_on_apps_dialog_title)
            .setMessage(R.string.draw_on_apps_dialog_message)
            .setPositiveButton(R.string.draw_on_apps_dialog_positive_button_text) { _, _ ->
                listener.positiveClick()
                this.dismiss()
            }
            .create()
    }

    companion object {
        fun newInstance(listener: OnResultListener): DrawOnScreenPermissionDialog {
            return DrawOnScreenPermissionDialog(listener)
        }
    }

    interface OnResultListener {
        fun positiveClick()
    }
}