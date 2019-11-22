package com.idev4droid.ble_ag_test

import ACTION_GATT_CONNECTED
import ACTION_GATT_DISCONNECTED
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Method


class MainActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 1
    private val SCAN_PERIOD: Long = 10000

    private var isConnected = false
        set(value) {
            field = value
            runOnUiThread {
                findViewById<TextView>(R.id.deviceInfoStatus).text = getString(if (field) R.string.connected else R.string.disconnect)
            }
        }
    private var handler = Handler()
    private var adapter: DeviceRecyclerViewAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var bleDevices: MutableLiveData<MutableList<BluetoothDevice>> = MutableLiveData()
    fun MutableLiveData<MutableList<BluetoothDevice>>.addDevice(device: BluetoothDevice) {
        val mutableList = this.value ?: mutableListOf()
        if (!mutableList.contains(device)) {
            mutableList.add(device)
            this.postValue(mutableList)
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensureBluetoothEnabled()

        val recyclerView = findViewById<RecyclerView>(R.id.deviceRecycler)
        adapter = DeviceRecyclerViewAdapter(LayoutInflater.from(this), onClickListener)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        bleDevices.observe(this,
            Observer<MutableList<BluetoothDevice>> {
                adapter?.updateData(it)
            })
    }

    private fun ensureBluetoothEnabled() {
        bluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    fun onScanButtonClicked(view: View) {
        bleDevices.postValue(mutableListOf())
        scanLeDevice(true)
    }

    fun onDisconnectClicked(view: View) {
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
    }

    private var mScanning: Boolean = false

    private fun scanLeDevice(enable: Boolean) {
        val bluetoothAdapter = bluetoothAdapter ?: return
        val scanButton = findViewById<Button>(R.id.scanButton)
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed({
                    mScanning = false
                    scanButton.text = getText(R.string.start_scan)
                    bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
                }, SCAN_PERIOD)
                mScanning = true
                scanButton.text = getText(R.string.scanning)
                bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
            }
            else -> {
                mScanning = false
                scanButton.text = getText(R.string.start_scan)
                bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
            }
        }
    }

    private val leScanCallback = object: ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.name ?: return
            bleDevices.addDevice(result.device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        }

        override fun onScanFailed(errorCode: Int) {
        }
    }

    private val onClickListener = object: DeviceOnClickListener {
        override fun onClick(device: BluetoothDevice) {
            bluetoothGatt = device.connectGatt(this@MainActivity, true, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("MATT", "CONNECTED")
                    intentAction = ACTION_GATT_CONNECTED
                    isConnected = true
                    runOnUiThread {
                        findViewById<TextView>(R.id.deviceInfoName).text = gatt?.device?.name
                    }
                    broadcastUpdate(intentAction)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("MATT", "DISCONNECTED")
                    intentAction = ACTION_GATT_DISCONNECTED
                    isConnected = false
                    broadcastUpdate(intentAction)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("MATT", "onServicesDiscovered success")
                gatt?.services?.forEach { service ->
                    service.characteristics.forEach {
                        gatt.readCharacteristic(it)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.i("MATT", "onCharacteristicRead $status ${characteristic?.uuid} $gatt ")
        }

        private fun broadcastUpdate(action: String) {
            val intent = Intent(action)
            sendBroadcast(intent)
        }
    }
}