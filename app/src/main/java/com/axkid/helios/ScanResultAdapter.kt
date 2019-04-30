package com.axkid.helios

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.technocreatives.beckon.BeckonScanResult
import kotlinx.android.synthetic.main.view_item_device.view.*

typealias OnSelectedResult = (BeckonScanResult) -> Unit

class ScanResultAdapter(
        private val layoutInflater: LayoutInflater,
        private val onClick: OnSelectedResult
) : RecyclerView.Adapter<ScanResultAdapter.DeviceVH>() {

    var items = emptyList<BeckonScanResult>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            DeviceVH(layoutInflater.inflate(R.layout.view_item_device, parent, false), onClick)

    override fun onBindViewHolder(holder: DeviceVH, position: Int) =
            holder.bind(items[position])

    override fun getItemCount() = items.size

    class DeviceVH(
            private val viewItem: View,
            private val onClick: OnSelectedResult
    ) : RecyclerView.ViewHolder(viewItem) {

        fun bind(device: BeckonScanResult) = with(viewItem) {
            tvDevice.text = device.macAddress
            setOnClickListener {
                onClick(device)
            }
        }
    }
}

