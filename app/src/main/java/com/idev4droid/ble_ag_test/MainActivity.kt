package com.idev4droid.ble_ag_test

import ACTION_GATT_CONNECTED
import ACTION_GATT_DISCONNECTED
import android.R.attr.x
import android.bluetooth.*
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


class MainActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 1
    private val SCAN_PERIOD: Long = 10000
    private val TAG = "BLE_TEST"

    private val CONTROL_POINT_SERVICE_UUID = "1f9fac00-65bc-3bbd-3f47-841f6a8bcdd8"

    private val DEVICE_CONTROL_CHARACTERISTIC_UUID = "1f9fac01-65bc-3bbd-3f47-841f6a8bcdd8"
    private val STEAMING_POINT_CHARACTERISTIC_UUID = "1f9fac04-65bc-3bbd-3f47-841f6a8bcdd8"

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
                    Log.i(TAG, "===============================")
                    Log.i(TAG, "CONNECTED ${gatt?.device?.name}")
                    Log.i(TAG, "===============================")
                    intentAction = ACTION_GATT_CONNECTED
                    isConnected = true
                    runOnUiThread {
                        findViewById<TextView>(R.id.deviceInfoName).text = gatt?.device?.name
                    }
                    broadcastUpdate(intentAction)
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "DISCONNECTED")
                    intentAction = ACTION_GATT_DISCONNECTED
                    isConnected = false
                    broadcastUpdate(intentAction)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.services?.forEach { service ->
                    if (service.uuid.toString() == CONTROL_POINT_SERVICE_UUID) {
                        service.characteristics.forEach {
                            if (it.uuid == UUID.fromString(STEAMING_POINT_CHARACTERISTIC_UUID)) {
                                //gatt.readCharacteristic(it)
                                gatt.setCharacteristicNotification(it, true)
                                val descriptor = it.getDescriptor(it.descriptors[0].uuid).apply {
                                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }
                                gatt.writeDescriptor(descriptor)
                                Log.i(TAG, "setCharacteristicNotification")
                            } else if (it.uuid == UUID.fromString(DEVICE_CONTROL_CHARACTERISTIC_UUID)) {
                                //val (randomShort, ret) = testPuff(it, gatt)
                                //Log.i(TAG, "Write bytes $randomShort - ${ret.toHexSpacedString()}")
                                handler.postDelayed({
                                    val ret = ByteArray(2)
                                    ret[0] = 0x2C.toByte()
                                    ret[1] = 0xF0.toByte()
                                    it.value = ret
                                    gatt.writeCharacteristic(it)
                                    Log.i(TAG, "Write bytes ${ret.toHexSpacedString()}")
                                }, 500)

                            }
                        }
                    } else {
                    }
                }
            } else {
                Log.i(TAG, "onServicesDiscovered failed $status")
            }
        }

        private fun testPuff(
            it: BluetoothGattCharacteristic,
            gatt: BluetoothGatt
        ): Pair<Int, ByteArray> {
            val randomShort = Random().nextInt(15000)
            val ret = ByteArray(4)
            ret[0] = 0x2F.toByte()
            ret[1] = 0xF0.toByte()
            ret[2] = (randomShort and 0xff).toByte()
            ret[3] = (randomShort shr 8 and 0xff).toByte()
            it.value = ret
            gatt.writeCharacteristic(it)
            return Pair(randomShort, ret)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {

        }

        private fun getRecordId(bytes: ByteArray): Short {
            val firstByte: Byte = ((bytes[1].toInt() and 0xF0) ushr 4).toByte()
            Log.i(TAG, "getRecordId first byte $firstByte")
            val secondByte: Byte = (((bytes[1].toInt() and 0x0F) shl 4) or ((bytes[2].toInt() and 0xF0) ushr 4)).toByte()
            Log.i(TAG, "getRecordId second byte $secondByte")
            return parseBytes(firstByte, secondByte, true)
        }

        private fun getRemainingCount(bytes: ByteArray): Short {
            val firstByte: Byte = bytes[3]
            val secondByte: Byte = (bytes[2].toInt() and 0x0F).toByte()
            return parseBytes(firstByte, secondByte)
        }

        private fun getChipId(bytes: ByteArray): String {
            val chipBytes = ByteArray(7)
            chipBytes[0] = bytes[4]
            chipBytes[1] = bytes[5]
            chipBytes[2] = bytes[6]
            chipBytes[3] = bytes[7]
            chipBytes[4] = bytes[8]
            chipBytes[5] = bytes[9]
            chipBytes[6] = bytes[10]
            return chipBytes.toHexString().toUpperCase()
        }

        private fun getTimestamp(bytes: ByteArray): Int {
            val firstByte = bytes[11]
            val secondByte = bytes[12]
            val thirdByte = bytes[13]
            val forthByte = bytes[14]
            return parseBytes(firstByte, secondByte, thirdByte, forthByte)
        }

        private fun getDuration(bytes: ByteArray): Short {
            val firstByte = bytes[15]
            val secondByte = bytes[16]
            return parseBytes(firstByte, secondByte)
        }

        private fun getOilCounter(bytes: ByteArray): Int {
            val firstByte = bytes[17]
            val secondByte = bytes[18]
            val thirdByte = bytes[19]
            val forthByte = 0x00.toByte()
            return parseBytes(firstByte, secondByte, thirdByte, forthByte)
        }

        fun ByteArray.toHexSpacedString() = joinToString("") { "%02x ".format(it) }
        fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

        fun parseBytes(firstByte: Byte, secondByte: Byte, bigEndian: Boolean = false): Short {
            val bb: ByteBuffer = ByteBuffer.allocate(2)
            bb.order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
            bb.put(firstByte)
            bb.put(secondByte)
            return bb.getShort(0)
        }

        fun parseBytes(firstByte: Byte, secondByte: Byte, thirdByte: Byte, forthByte: Byte): Int {
            val bb: ByteBuffer = ByteBuffer.allocate(4)
            bb.order(ByteOrder.LITTLE_ENDIAN)
            bb.put(firstByte)
            bb.put(secondByte)
            bb.put(thirdByte)
            bb.put(forthByte)
            return bb.getInt(0)
        }

        private fun broadcastUpdate(action: String) {
            val intent = Intent(action)
            sendBroadcast(intent)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.i(TAG, "onCharacteristicWrite ${characteristic?.uuid} $status")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.i(TAG, "onDescriptorWrite $descriptor")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val bytes = characteristic?.value ?: return
            if (characteristic.uuid == UUID.fromString(STEAMING_POINT_CHARACTERISTIC_UUID)) {
                Log.i(
                    TAG,
                    "${characteristic.uuid} properties ${characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE or
                            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0} original byte ${bytes.toHexSpacedString()}"
                )

                val recordId = getRecordId(bytes)
                val remainingCount = getRemainingCount(bytes)
                val chipId = getChipId(bytes)
                val timestamp = getTimestamp(bytes)
                val duration = getDuration(bytes)
                val oilCounter = getOilCounter(bytes)

                runOnUiThread {
                    findViewById<TextView>(R.id.deviceInfoRecordId).text = recordId.toString()
                    findViewById<TextView>(R.id.deviceInfoRemainingCount).text =
                        remainingCount.toString()
                    findViewById<TextView>(R.id.deviceInfoChipId).text = chipId
                    findViewById<TextView>(R.id.deviceInfoTimestamp).text = timestamp.toString()
                    findViewById<TextView>(R.id.deviceInfoDuration).text = duration.toString()
                    findViewById<TextView>(R.id.deviceInfoOilCounter).text = oilCounter.toString()
                }
            } else if (characteristic.uuid == UUID.fromString(STEAMING_POINT_CHARACTERISTIC_UUID)) {
                Log.i(
                    TAG,
                    "${characteristic.uuid} properties ${characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE or
                            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0} original byte ${bytes.toHexSpacedString()}"
                )
            }
        }
    }
}