package jp.tukutano.tomagps.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.tukutano.tomagps.databinding.ItemLogCardBinding
import jp.tukutano.tomagps.model.JourneyLog
import com.bumptech.glide.Glide

import jp.tukutano.tomagps.R
/**
 * ログ一覧のRecyclerView Adapter
 */
class LogAdapter(
    private val listener: Listener
) : ListAdapter<JourneyLog, LogAdapter.VH>(DIFF) {

    interface Listener {
        fun onItemClicked(log: JourneyLog)
        fun onDeleteClicked(log: JourneyLog)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLogCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding, listener)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemLogCardBinding,
        private val listener: Listener
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnDelete.setOnClickListener {
                binding.log?.let { log ->
                    listener.onDeleteClicked(log)
                }
            }
            binding.root.setOnClickListener {
                binding.log?.let { log ->
                    listener.onItemClicked(log)
                }
            }
        }

        fun bind(log: JourneyLog) {
            binding.log = log
            binding.listener = listener  // ここで listener を設定

            // サムネイル読み込み
            binding.imgThumb.apply {
                // Glide で Uri 画像をロードし、ない場合はプレースホルダー
                log.thumbnail?.let { uri ->
                    Glide.with(this)
                        .load(uri)
                        .placeholder(R.drawable.ic_placeholder)
                        .into(this)
                } ?: run {
                    setImageResource(R.drawable.ic_placeholder)
                }
            }

            binding.executePendingBindings()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<JourneyLog>() {
            override fun areItemsTheSame(old: JourneyLog, new: JourneyLog) = old.id == new.id
            override fun areContentsTheSame(old: JourneyLog, new: JourneyLog) = old == new
        }
    }
}
