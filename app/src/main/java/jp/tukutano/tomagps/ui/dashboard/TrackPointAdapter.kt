// ui/dashboard/TrackPointAdapter.kt
package jp.tukutano.tomagps.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.tukutano.tomagps.databinding.ItemTrackpointBinding
import jp.tukutano.tomagps.service.TrackPoint
import java.text.SimpleDateFormat
import java.util.*

class TrackPointAdapter :
    ListAdapter<TrackPoint, TrackPointAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TrackPoint>() {
            override fun areItemsTheSame(old: TrackPoint, new: TrackPoint) =
                old.time == new.time

            override fun areContentsTheSame(old: TrackPoint, new: TrackPoint) =
                old == new
        }
        private val DATE_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    inner class ViewHolder(private val binding: ItemTrackpointBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TrackPoint) = with(binding) {
            txtLat.text  = String.format("%.5f", item.lat)
            txtLng.text  = String.format("%.5f", item.lng)
            txtTime.text = DATE_FMT.format(Date(item.time))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemTrackpointBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
