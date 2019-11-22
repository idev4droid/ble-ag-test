package com.idev4droid.ble_ag_test

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class DeviceRecyclerViewAdapter(private val inflater: LayoutInflater, private val listener: DeviceOnClickListener) : RecyclerView.Adapter<DeviceRecyclerViewAdapter.ViewHolder>() {

    private var mData: List<BluetoothDevice>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = inflater.inflate(R.layout.device_item, parent, false)
        return ViewHolder(view, listener)
    }

    override fun getItemCount(): Int {
        return mData?.size ?: 0
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = mData?.get(position) ?: return
        holder.setDevice(device)
    }

    fun updateData(devices: List<BluetoothDevice>) {
        mData = devices
        notifyDataSetChanged()
    }

    class ViewHolder internal constructor(itemView: View, private val listener: DeviceOnClickListener) : RecyclerView.ViewHolder(itemView) {
        private var deviceNameTextView: TextView = itemView.findViewById(R.id.deviceNameText)
        private var deviceConnectButton: Button = itemView.findViewById(R.id.deviceConnectButton)

        fun setDevice(device: BluetoothDevice) {
            deviceNameTextView.text = device.name
            deviceConnectButton.setOnClickListener{
                listener.onClick(device)
            }
        }
    }


}