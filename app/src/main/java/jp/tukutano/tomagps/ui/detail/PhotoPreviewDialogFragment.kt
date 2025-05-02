package jp.tukutano.tomagps.ui.detail

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import jp.tukutano.tomagps.databinding.DialogPhotoPreviewBinding
import com.google.android.gms.maps.model.Marker

/**
 * 撮影写真をプレビュー表示する DialogFragment
 * 引数に写真の URI を文字列で渡す
 */
class PhotoPreviewDialogFragment : DialogFragment() {

    private var _binding: DialogPhotoPreviewBinding? = null
    private val binding get() = _binding!!
    private var imageUri: Uri? = null

    // ダイアログに紐づくマーカー
    private var marker: Marker? = null

    /**
     * ダイアログ生成前にマーカーをセット
     */
    fun setMarker(marker: Marker): PhotoPreviewDialogFragment = apply {
        this.marker = marker
    }


    companion object {
        private const val ARG_URI = "ARG_URI"
        const val REQUEST_KEY_PHOTO_DELETED = "photo_deleted"
        const val KEY_URI = "uri"

        /**
         * インスタンス生成メソッド
         * @param uriString Uri.toString() 形式の文字列
         */
        fun newInstance(uriString: String): PhotoPreviewDialogFragment =
            PhotoPreviewDialogFragment().apply {
                arguments = bundleOf(ARG_URI to uriString)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 文字列から Uri を正しく取得
        val uriStr = requireArguments().getString(ARG_URI)
        imageUri = uriStr?.let { Uri.parse(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPhotoPreviewBinding.inflate(LayoutInflater.from(context))
        // URI がある場合にプレビュー
        imageUri?.let { binding.ivPreview.setImageURI(it) }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton("削除") { _, _ ->
                imageUri?.let { uri ->
                    // FragmentResult で TrackFragment へ通知
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY_PHOTO_DELETED,
                        bundleOf(KEY_URI to uri)
                    )
                }
                // 地図上のマーカーを消去
                marker?.remove()
            }
            .setNegativeButton("閉じる", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
