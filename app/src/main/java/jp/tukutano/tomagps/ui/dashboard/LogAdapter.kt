package jp.tukutano.tomagps.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import jp.tukutano.tomagps.R
import jp.tukutano.tomagps.databinding.ItemLogCardBinding
import jp.tukutano.tomagps.model.JourneyLog

class LogAdapter(
    private val onClick: (JourneyLog) -> Unit
) : ListAdapter<JourneyLog, LogAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<JourneyLog>() {
            override fun areItemsTheSame(old: JourneyLog, new: JourneyLog) = old.id == new.id
            override fun areContentsTheSame(old: JourneyLog, new: JourneyLog) = old == new
        }
    }

    inner class ViewHolder(
        private val binding: ItemLogCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JourneyLog) = with(binding) {
            txtTitle.text      = item.title
            txtDate.text       = item.date.toLocalDate().toString()
            txtDistance.text   = String.format("%.1f km", item.distanceKm)

            // Glide/Picasso 等でサムネイル読み込み例
            item.thumbnail?.let { uri ->
                Glide.with(imgThumb).load(uri).into(imgThumb)
            } ?: imgThumb.setImageResource(R.drawable.ic_placeholder)

            root.setOnClickListener { onClick(item) }

            btnDetail.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemLogCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

}