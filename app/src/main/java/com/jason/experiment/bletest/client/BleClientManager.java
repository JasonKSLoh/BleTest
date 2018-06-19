package com.jason.experiment.bletest.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.jason.experiment.bletest.utils.UuidUtils;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BleClientManager
 * Created by jason on 13/6/18.
 */
public class BleClientManager {

    private static final String LOG_TAG = "+_BleCliMgr";

    private Context               context;

    private BluetoothDevice       bluetoothDevice;
    private BluetoothManager      bluetoothManager;
    private BluetoothAdapter      bluetoothAdapter;
    private BluetoothGatt         bluetoothGatt;
    private BluetoothGattCallback gattCallback;
    private boolean isInitialized = false;
    Queue<byte[]> outputQueue = new LinkedBlockingQueue<>();
    AtomicBoolean isWriting;
    BleClientCallback clientCallback;
    private final BroadcastReceiver bluetoothReceiver;
    private String address;
    Handler handler;

    public BleClientManager(){
        handler = new Handler();
        isWriting = new AtomicBoolean(false);
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        startClient();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        stopClient();
                        break;
                    default:
                        // Do nothing
                        break;
                }
            }
        };
        setupGattCallback();
    }

    public void addToQueue(byte[] data){
        Log.d(LOG_TAG, "Item added to queue: " + new String(data, StandardCharsets.US_ASCII));
        outputQueue.add(data);
    }

    public BluetoothDevice getConnectedDevice(){
        if(bluetoothGatt == null) {
            return null;
        } else {
            return bluetoothGatt.getDevice();
        }
    }
//    public void writeDataToServer() {
//        if(isWriting.get()){
//            Log.d(LOG_TAG, "isWriting is locked");
//            return;
//        } else {
//            isWriting.set(true);
//            while(outputQueue.peek() != null){
//                Log.d(LOG_TAG, "isWriting bytes");
//                byte[] data = outputQueue.poll();
//                BluetoothGattCharacteristic clientOut = bluetoothGatt
//                        .getService(UuidUtils.getServiceUuid())
//                        .getCharacteristic(UuidUtils.getClientOutUuid());
//                clientOut.setValue(data);
//                bluetoothGatt.writeCharacteristic(clientOut);
//            }
//            isWriting.set(false);
//        }
//    }

    public void writeDataToServer() {
        if(isWriting.get()){
            Log.d(LOG_TAG, "isWriting is locked");
            return;
        } else {
            handler.removeCallbacksAndMessages(null);
            isWriting.set(true);
            if(outputQueue.peek() != null){
                Log.d(LOG_TAG, "isWriting bytes");
                byte[] data = outputQueue.poll();
                BluetoothGattCharacteristic clientOut = bluetoothGatt
                        .getService(UuidUtils.getServiceUuid())
                        .getCharacteristic(UuidUtils.getClientOutUuid());
                clientOut.setValue(data);
                bluetoothGatt.writeCharacteristic(clientOut);
            } else {
                Log.d(LOG_TAG, "Queue was empty");
            }
            handler.postDelayed(()->{
                isWriting.set(false);
            }, 500);
//            isWriting.set(false);
        }
    }


    public boolean initBleClientManager(@NonNull Context context, @NonNull BleClientCallback bleClientCallback, @NonNull String address) {
        this.context = context;
        this.clientCallback = bleClientCallback;
        this.address = address;
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return false;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return false;
        }
        bluetoothAdapter.setName("BleTest-Client");
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(bluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(LOG_TAG, "Bluetooth is currently disabled... enabling");
            bluetoothAdapter.enable();
            isInitialized = startClient();
        } else {
            Log.i(LOG_TAG, "Bluetooth enabled... starting client");
            isInitialized = startClient();
        }
        return isInitialized;
    }

    public void destroyInstance() {
        clientCallback = null;
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            stopClient();
        }
        if(context != null){
            context.unregisterReceiver(bluetoothReceiver);
        }
        context = null;
        address = null;
    }

    private boolean startClient() {
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback);
        Log.d(LOG_TAG, "Client Started, Gatt OK: " + (bluetoothGatt != null));
        return bluetoothGatt != null;
    }

    private void stopClient() {
        Log.d(LOG_TAG, "Client Stopped");
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        if (bluetoothAdapter != null) {
            bluetoothAdapter = null;
        }
    }

    private void setupGattCallback(){
        gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("+_", "Connected to GATT client. Attempting to start service discovery");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("+_", "Disconnected from GATT client");
                    clientCallback.deviceConnected(false);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(LOG_TAG, "GATT Services discovered: Success");
                    boolean connected = false;

                    BluetoothGattService service = gatt.getService(UuidUtils.getServiceUuid());
                    if (service != null) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UuidUtils.getServerOutUuid());
                        if (characteristic != null) {
                            boolean setNotifSuccess = gatt.setCharacteristicNotification(characteristic, true);
                            Log.d(LOG_TAG, "Set Notif Success: " + setNotifSuccess);
                            BluetoothGattCharacteristic       notificationCharacteristic = service.getCharacteristic(UuidUtils.getNotificationUuid());

                            if (notificationCharacteristic != null) {
                                boolean setValueSuccess = notificationCharacteristic.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                Log.d(LOG_TAG, "Set Value success: " + setValueSuccess);
                                connected = gatt.writeCharacteristic(notificationCharacteristic);
                            }
                        }
                    }
                    clientCallback.deviceConnected(connected);
                } else {
                    Log.d("+_", "onServicesDiscovered received: " + status);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                Log.d(LOG_TAG, "GATT - OnCharacteristicRead");
                readServerOutCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                Log.d(LOG_TAG, "GATT - OnCharacteristicChanged");
                readServerOutCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if(status == BluetoothGatt.GATT_SUCCESS){
                    Log.d(LOG_TAG, "Gatt Write success");
                    if(characteristic.getUuid().equals(UuidUtils.getClientOutUuid())){
                        isWriting.set(false);
                        if(outputQueue.peek() != null){
                            Log.d("+_", "outputqueue was not empty, continuing to write");
                            writeDataToServer();
                        }
                    }
                } else {
                    Log.d(LOG_TAG, "Gatt write failure");
                }
            }

            private void readServerOutCharacteristic(BluetoothGattCharacteristic characteristic) {
                if (characteristic.getUuid().equals(UuidUtils.getServerOutUuid())) {
                    byte[] data = characteristic.getValue();
//                    String dataString = new String(data, StandardCharsets.US_ASCII);
                    clientCallback.onServerMessageReceived(data);
                }
            }
        };
    }

    public interface BleClientCallback{
        void deviceConnected(boolean success);
        void onServerMessageReceived(byte[] data);
    }

}
