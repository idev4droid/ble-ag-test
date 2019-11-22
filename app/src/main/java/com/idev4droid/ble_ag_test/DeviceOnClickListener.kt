package com.idev4droid.ble_ag_test

import android.bluetooth.BluetoothDevice
import android.view.View

interface DeviceOnClickListener {
    fun onClick(device: BluetoothDevice)
}