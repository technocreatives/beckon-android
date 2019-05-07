package com.axkid.helios

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.technocreatives.beckon.BeckonDevice
import com.technocreatives.beckon.DiscoveredDevice
import kotlinx.android.synthetic.main.view_item_device.view.*

typealias OnSelectDevice = (DiscoveredDevice) -> Unit

class DeviceAdapter(
        private val layoutInflater: LayoutInflater,
        private val onClick: OnSelectDevice
) : RecyclerView.Adapter<DeviceAdapter.DeviceVH>() {

    var items = emptyList<DiscoveredDevice>()
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
            private val onClick: OnSelectDevice
    ) : RecyclerView.ViewHolder(viewItem) {

        fun bind(device: DiscoveredDevice) = with(viewItem) {
            tvDevice.text = device.toString()
            setOnClickListener {
                onClick(device)
            }
        }
    }
}

