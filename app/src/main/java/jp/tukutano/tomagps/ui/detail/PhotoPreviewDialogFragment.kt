package jp.tukutano.tomagps.ui.detail

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import jp.tukutano.tomagps.R

/**
 * 撮影写真をプレビュー表示する DialogFragment
 * 引数に写真の URI を文字列で渡す
 */
class PhotoPreviewDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_URI = "ARG_URI"

        /**
         * インスタンス生成用
         * @param uriString Uri.toString() 形式の文字列
         */
        fun newInstance(uriString: String): PhotoPreviewDialogFragment {
            return PhotoPreviewDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uriString)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val uriString = requireArguments().getString(ARG_URI)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_photo_preview, null)
        val imageView = view.findViewById<ImageView>(R.id.ivPreview)

        // Glide で画像をロード
        if (!uriString.isNullOrEmpty()) {
            Glide.with(this)
                .load(Uri.parse(uriString))
                .placeholder(R.drawable.ic_placeholder)
                .into(imageView)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("閉じる", null)
            .create()
    }
}