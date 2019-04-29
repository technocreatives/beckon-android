package com.axkid.helios.feature.test

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.axkid.helios.R
import kotlinx.android.synthetic.main.view_item_device.view.*

typealias OnSelectDevice = (BluetoothDevice) -> Unit

class DeviceAdapter(
    private val layoutInflater: LayoutInflater,
    private val onClick: OnSelectDevice
) : RecyclerView.Adapter<DeviceVH>() {

    var items = emptyList<BluetoothDevice>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        DeviceVH(layoutInflater.inflate(R.layout.view_item_device, parent, false), onClick)

    override fun onBindViewHolder(holder: DeviceVH, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}

class DeviceVH(
    private val viewItem: View,
    private val onClick: OnSelectDevice
) : RecyclerView.ViewHolder(viewItem) {
    fun bind(device: BluetoothDevice) = with(viewItem) {
        tvDevice.text = device.address
        setOnClickListener {
            onClick(device)
        }
    }
}