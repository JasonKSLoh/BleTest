package com.jason.experiment.bletest.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jason.experiment.bletest.R;
import com.jason.experiment.bletest.utils.QrManager;
import com.jason.experiment.bletest.utils.UuidUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * BleServerActivity
 * Created by jason on 13/6/18.
 */
public class BleServerActivity extends AppCompatActivity {
    private BluetoothManager            bluetoothManager;
    private BluetoothAdapter            bluetoothAdapter;
    private BluetoothLeAdvertiser       bluetoothLeAdvertiser;
    private BluetoothGattServer         bluetoothGattServer;
    private BroadcastReceiver           bluetoothReceiver;
    private AdvertiseSettings           advertiseSettings;
    private AdvertiseData               advertiseData;
    private AdvertiseCallback           advertiseCallback;
    private BluetoothGattServerCallback gattServerCallback;
    private Queue<byte[]> outputQueue = new LinkedBlockingQueue<>();
    private SortedSet<BluetoothDevice> registeredDevices;
    private AtomicBoolean isNotifying   = new AtomicBoolean(false);
    private boolean       isAdvertising = true;

    private PublishSubject<byte[]> queuePublishSubject = PublishSubject.create();
    private CompositeDisposable    queueDisposables    = new CompositeDisposable();

    private static String LOG_TAG = "LOG_TAGBleServer";

    private RecyclerView      rvClientList;
    private Button            btnStartStop;
    private Button            btnShowQr;
    private EditText          etServerMessage;
    private TextView          tvMessages;
    private ClientListAdapter adapter;

    private String messages = "Message Log\n";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_server);
        setupViews();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        initCallbacks();
        if (setUpBle()) {
            startAll();
        }
    }

    private void setupViews() {
        rvClientList = findViewById(R.id.rv_server_clientlist);
        btnStartStop = findViewById(R.id.btn_server_startstop);
        btnShowQr = findViewById(R.id.btn_show_qr);
        tvMessages = findViewById(R.id.tv_server_messages);
        tvMessages.setText(messages);
        etServerMessage = findViewById(R.id.et_server_message);
        etServerMessage.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String sentMessage = formatMessageForSending(etServerMessage.getText().toString());
                addStringToQueue(sentMessage);
                etServerMessage.setText("");
                handled = true;
            }
            return handled;
        });
        tvMessages.setMovementMethod(new ScrollingMovementMethod());
        btnStartStop.setOnClickListener(v -> onStartStopButtonPressed());
        btnShowQr.setOnClickListener(v -> onShowQrPressed());
        registeredDevices = new TreeSet<>((o1, o2) -> {
            if (o1 == null
                || o2 == null
                || o1.getAddress() == null
                || o2.getAddress() == null) {
                return 0;
            }
            return o1.getAddress().compareTo(o2.getAddress());
        });
        adapter = new ClientListAdapter(registeredDevices);
        rvClientList.setLayoutManager(new LinearLayoutManager(this));
        rvClientList.setHasFixedSize(true);
        rvClientList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        rvClientList.setAdapter(adapter);
    }

    public boolean setUpBle() {
        boolean setupSuccess = false;
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothAdapter.setName("BleTest-Server");
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            setupSuccess = isBluetoothOn();
        }

        if (setupSuccess) {
            IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            setupReceivers();
            registerReceiver(bluetoothReceiver, intentFilter);
        } else {
            finish();
            Toast.makeText(getApplicationContext(), "Please Turn Bluetooth On", Toast.LENGTH_SHORT).show();
        }

        return setupSuccess;
    }

    private void setUpDisposables() {
        Disposable notificationDisposable = queuePublishSubject.subscribeOn(Schedulers.io())
                .subscribe(b -> {
                    notifyDevices();
                });
        queueDisposables.add(notificationDisposable);
    }

    private void setupReceivers() {
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        startAll();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        stopAll();
                        break;
                    default:
                        // Do nothing
                }
            }
        };
    }

    private boolean isBluetoothOn() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null
               && defaultAdapter.isEnabled()
               && getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }
    
    //Uses permission Local_Mac_Address
    private void onShowQrPressed() {
        String currentAddress = bluetoothAdapter.getAddress();
        if (bluetoothAdapter == null || currentAddress == null || currentAddress.equals("02:00:00:00:00:00")) {
            if ((currentAddress = getBtAddressViaReflection()) == null) {
                Toast.makeText(this, "Turn on the Bluetooth Server first", Toast.LENGTH_SHORT).show();
            } else {
                showQrSnackbar(currentAddress);
            }
        } else {
            showQrSnackbar(currentAddress);
        }
    }

    private static String getBtAddressViaReflection() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        try {
            Field targetField = bluetoothAdapter.getClass().getDeclaredField("mService");
            targetField.setAccessible(true);
            Object bluetoothManagerService = targetField.get(bluetoothAdapter);
            if (bluetoothManagerService == null) {
                Log.d(LOG_TAG, "couldn't find bluetoothManagerService");
                return null;
            }
            Method method = bluetoothManagerService.getClass().getDeclaredMethod("getAddress");
            method.setAccessible(true);
            Object address = method.invoke(bluetoothManagerService);

            if (address != null && address instanceof String) {
                Log.w(LOG_TAG, "using reflection to get the BT MAC address: " + address);
                return (String) address;
            } else {
                return null;
            }

        } catch (SecurityException se) {
            Log.d("LOG_TAG", "Unable to get field via reflection");
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showQrSnackbar(String qrData) {
        View     view     = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(view, "", Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        final View snackbarView = snackbar.getView();
        view.setPadding(0, 0, 0, 0);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int                       width           = displayMetrics.widthPixels;
        Bitmap                    bitmap          = QrManager.getQrCode(qrData, width, width);
        ImageView                 imageView       = new ImageView(this);
        LinearLayout.LayoutParams imageViewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageView.setLayoutParams(imageViewParams);
        imageView.setBackground(new BitmapDrawable(getResources(), bitmap));
        imageView.setClickable(true);
        imageView.setOnClickListener(v -> snackbar.dismiss());

        Snackbar.SnackbarLayout layout   = (Snackbar.SnackbarLayout) snackbarView;
        TextView                textView = layout.findViewById(android.support.design.R.id.snackbar_text);
        textView.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams snackbarParams = (FrameLayout.LayoutParams) layout.getLayoutParams();
        snackbarParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        layout.addView(imageView, snackbarParams);
    }

    public String formatMessageForSending(String message) {
        String id        = bluetoothAdapter.getName();
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis());
        String output    = "User: [" + id + "] at [" + timeStamp + "]\n\t";
        output += message.replaceAll("\n", "\n\t") + '\n';
        return output;
    }

    public void updateMessagesDisplay(byte[] incomingData) {
        String messageText    = tvMessages.getText().toString();
        String incomingString = new String(incomingData, StandardCharsets.US_ASCII);
        messageText += incomingString;
        Log.d("LOG_TAG", "Updated messages: " + incomingString);
        tvMessages.setText(messageText);
    }

    public void onStartStopButtonPressed() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isAdvertising) {
            stopAll();
        } else {
            startAll();
        }
        isAdvertising = !isAdvertising;
    }

    public void addStringToQueue(final String dataString) {
        if (dataString == null || dataString.isEmpty() || !isAdvertising) {
            return;
        }
        addDataToQueue(dataString.getBytes(StandardCharsets.US_ASCII));
    }

    public void addDataToQueue(byte[] data) {
        if (data == null || data.length == 0 || !isAdvertising) {
            return;
        }
        int index      = 0;
        int dataLength = data.length;
        while (dataLength - index > 20) {
            byte[] chunk = Arrays.copyOfRange(data, index, index += 20);
            outputQueue.add(chunk);
            queuePublishSubject.onNext(chunk);
        }
        byte[] lastBytes = Arrays.copyOfRange(data, index, data.length);
        outputQueue.add(lastBytes);
        queuePublishSubject.onNext(lastBytes);
    }

    public void stopAll() {
        stopAdvertising();
        stopServer();
        adapter.clearDevices();
        clearMessageQueue();
        btnStartStop.setText(getString(R.string.start_server));
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
    }

    public void startAll() {
        startServer();
        startAdvertising();
        btnStartStop.setText(getString(R.string.stop_server));
        btnStartStop.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            stopAdvertising();
            stopServer();
        }
        if(bluetoothReceiver != null){
            unregisterReceiver(bluetoothReceiver);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        queueDisposables.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpDisposables();
    }

    public void startAdvertising() {
        advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();
        advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(UuidUtils.getServiceUuid()))
                .build();

        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
    }

    public void stopAdvertising() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        }
    }

    public void startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback);
        if (bluetoothGattServer != null) {
            bluetoothGattServer.addService(createBleMessagingService());
        }
    }

    public void stopServer() {
        if (bluetoothGattServer != null) {
            bluetoothGattServer.close();
        }
    }

    private BluetoothGattService createBleMessagingService() {
        BluetoothGattService service = new BluetoothGattService(UuidUtils.getServiceUuid(), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic clientOut = new BluetoothGattCharacteristic(UuidUtils.getClientOutUuid(),
                                                                                BluetoothGattCharacteristic.PROPERTY_READ
                                                                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                                                                | BluetoothGattCharacteristic.PROPERTY_WRITE,
                                                                                BluetoothGattCharacteristic.PERMISSION_READ
                                                                                | BluetoothGattCharacteristic.PERMISSION_WRITE);

        BluetoothGattCharacteristic serverOut = new BluetoothGattCharacteristic(UuidUtils.getServerOutUuid(),
                                                                                BluetoothGattCharacteristic.PROPERTY_READ
                                                                                | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                                                                BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic notification = new BluetoothGattCharacteristic(UuidUtils.getNotificationUuid(),
                                                                                   BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                                                                   | BluetoothGattCharacteristic.PROPERTY_WRITE
                                                                                   | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                                                                                   BluetoothGattCharacteristic.PERMISSION_WRITE
                                                                                   | BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(clientOut);
        service.addCharacteristic(serverOut);
        service.addCharacteristic(notification);
        return service;
    }

    private void notifyDevices() {
        if (isNotifying.get()) {
            return;
        } else {
            isNotifying.set(true);
            if (registeredDevices.isEmpty()) {
                Log.i("LOG_TAG", "No subscribers registered");
                isNotifying.set(false);
                return;
            }
            while (outputQueue.peek() != null) {
                byte[] data = outputQueue.poll();
                Log.i("LOG_TAG", "Sending update to " + registeredDevices.size() + " subscribers");
                runOnUiThread(() -> updateMessagesDisplay(data));
                for (BluetoothDevice device : registeredDevices) {
                    BluetoothGattCharacteristic serverOutCharacteristic = bluetoothGattServer
                            .getService(UuidUtils.getServiceUuid())
                            .getCharacteristic(UuidUtils.getServerOutUuid());
                    serverOutCharacteristic.setValue(data);
                    bluetoothGattServer.notifyCharacteristicChanged(device, serverOutCharacteristic, false);
                }
            }
            isNotifying.set(false);
        }
    }

    private void clearMessageQueue() {
//        if (isNotifying.get()) {
//            return;
//        } else {
//            isNotifying.set(true);
        outputQueue.clear();
//            isNotifying.set(false);
//        }
    }

    private void initCallbacks() {
        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.i("LOG_TAG", "LE Advertise Started.");
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.w("LOG_TAG", "LE Advertise Failed: " + errorCode);
            }
        };

        gattServerCallback = new BluetoothGattServerCallback() {

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(LOG_TAG, "BluetoothDevice CONNECTED: " + device);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(LOG_TAG, "BluetoothDevice DISCONNECTED: " + device);
                    //Remove device from any active subscriptions
                    runOnUiThread(() -> adapter.removeDevice(device));

//                    registeredDevices.remove(device);
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                    BluetoothGattCharacteristic characteristic) {
                if (UuidUtils.getServerOutUuid().equals(characteristic.getUuid())) {
                    Log.d(LOG_TAG, "Read Server Out");
                    bluetoothGattServer.sendResponse(device,
                                                     requestId,
                                                     BluetoothGatt.GATT_SUCCESS,
                                                     0,
                                                     outputQueue.peek());
                } else {
                    // Invalid characteristic
                    Log.d(LOG_TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                    bluetoothGattServer.sendResponse(device,
                                                     requestId,
                                                     BluetoothGatt.GATT_FAILURE,
                                                     0,
                                                     null);
                }
            }


            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattDescriptor descriptor) {
                bluetoothGattServer.sendResponse(device,
                                                 requestId,
                                                 BluetoothGatt.GATT_FAILURE,
                                                 0,
                                                 null);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                     int requestId,
                                                     BluetoothGattCharacteristic characteristic,
                                                     boolean preparedWrite,
                                                     boolean responseNeeded,
                                                     int offset,
                                                     byte[] value) {
                if (UuidUtils.getNotificationUuid().equals(characteristic.getUuid())) {
                    if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
//                        registeredDevices.add(device);
                        runOnUiThread(() -> adapter.addDevice(device));

                    } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
//                        registeredDevices.remove(device);
                        runOnUiThread(() -> adapter.removeDevice(device));

                    }
                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device,
                                                         requestId,
                                                         BluetoothGatt.GATT_SUCCESS,
                                                         0,
                                                         null);
                    }
                } else if (UuidUtils.getClientOutUuid().equals(characteristic.getUuid())) {
                    Log.d("LOG_TAG", "Got serverout UUID write request");
                    if (responseNeeded) {
                        Log.d("LOG_TAG", "Sending success resp");
                        bluetoothGattServer.sendResponse(device,
                                                         requestId,
                                                         BluetoothGatt.GATT_SUCCESS,
                                                         0,
                                                         null);
                    }
                    addDataToQueue(value);
                } else {
                    Log.d(LOG_TAG, "Unknown descriptor write request");
                    if (responseNeeded) {
                        bluetoothGattServer.sendResponse(device,
                                                         requestId,
                                                         BluetoothGatt.GATT_FAILURE,
                                                         0,
                                                         null);
                    }
                }
            }
        };
    }

}
