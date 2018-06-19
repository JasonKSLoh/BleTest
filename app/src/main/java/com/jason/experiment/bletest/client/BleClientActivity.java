package com.jason.experiment.bletest.client;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jason.experiment.bletest.R;
import com.jason.experiment.bletest.client.qr_scanner.ScannerActivity;
import com.jason.experiment.bletest.utils.PermissionUtils;
import com.jason.experiment.bletest.utils.UuidUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * BleClientActivity
 * Created by jason on 13/6/18.
 */
public class BleClientActivity extends AppCompatActivity implements ScanResultsAdapter.ScanSelectedCallback, BleClientManager.BleClientCallback {

    Button             btnScanBle;
    Button             btnScanQr;
    RecyclerView       rvScanResults;
    ScanResultsAdapter adapter;
    TextView           tvSelectedDevice;
    EditText           etClientMessage;
    TextView           tvMessages;
    ProgressBar        progressBar;

    private static final int SCAN_TIMEOUT                         = 10000;
    private static final int REQUEST_CODE_CAMERA_ACTIVITY         = 1;
    private static final int REQUEST_CODE_CAMERA_PERMISSION       = 2;
    private static final int REQUEST_CODE_SHOW_BLUETOOTH_SETTINGS = 2222;

    BluetoothLeScannerCompat scanner;
    ScanFilter               scanFilter;
    ScanSettings             scanSettings;
    ScanCallback             scanCallback;

    BleClientManager bleClientManager;
    List<ScanResult> scanResults = new ArrayList<>();

    private PublishSubject<byte[]> queuePublishSubject = PublishSubject.create();
    CompositeDisposable compositeDisposable = new CompositeDisposable();

    List<String> foundMacs             = new ArrayList<>();
    Handler      scanWatchdogHandler   = new Handler();
    String       selectedDeviceAddress = "";
    String       selectedDeviceName    = "";
    boolean      isConnected           = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_client);
        setUpViews();

        setupBleScanner();
        setupObservables();
    }

    public void setupObservables() {
        Disposable notificationDisposable = queuePublishSubject.subscribeOn(Schedulers.io())
                .subscribe(b -> {
                    Log.d("+_", "Queuepublishsubject OnNext Called");
                    bleClientManager.addToQueue(b);
                    bleClientManager.writeDataToServer();
                });
        compositeDisposable.add(notificationDisposable);
    }

    public void setupBleScanner() {
        scanner = BluetoothLeScannerCompat.getScanner();
        scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(1000)
                .build();
        scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UuidUtils.getServiceUuid()))
                .build();
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                //Do Nothing
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                if (!results.isEmpty()) {
                    for (ScanResult result : results) {
                        String foundAddress = result.getDevice().getAddress();
                        String foundName    = result.getDevice().getName();
                        if (!foundMacs.contains(foundName)) {
                            Log.d("+_", "Added device: " + foundName + ": " + foundAddress);
                            foundMacs.add(foundName);
                            adapter.addScannedDevice(result);
                            resetScanTimeoutWatchdog();
                        }
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.d("+_", "Scan Failed");
            }
        };
    }

    public void setUpViews() {
        tvSelectedDevice = findViewById(R.id.tv_selected_device);
        rvScanResults = findViewById(R.id.rv_scan_results);
        btnScanBle = findViewById(R.id.btn_scan_ble);
        btnScanQr = findViewById(R.id.btn_scan_qr);
        tvMessages = findViewById(R.id.tv_client_messages);
        etClientMessage = findViewById(R.id.et_client_message);
        progressBar = findViewById(R.id.progressbar_scan);

        adapter = new ScanResultsAdapter(scanResults, this);
        rvScanResults.setHasFixedSize(true);
        rvScanResults.setLayoutManager(new LinearLayoutManager(this));
        rvScanResults.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.HORIZONTAL));
        rvScanResults.setAdapter(adapter);

        btnScanBle.setOnClickListener(v -> {
            if (!isConnected) {
                onScanBleClicked();
            } else {
                decoupleDevice();
            }
        });

        btnScanQr.setOnClickListener(v -> {
            onScanQrClicked();
        });

        etClientMessage.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                Log.d("+_", "Ime Done called");
                String sentMessage = formatMessageForSending(etClientMessage.getText().toString());
                Log.d("+_", "Sent message: " + sentMessage);
                writeStringToServer(sentMessage);
                etClientMessage.setText("");
                handled = true;
            }
            return handled;
        });
    }

    private void onScanBleClicked() {
        progressBar.setVisibility(View.VISIBLE);

        adapter.clearScannedDevices();
        foundMacs.clear();
        resetScanTimeoutWatchdog();

        if(BluetoothAdapter.getDefaultAdapter().isEnabled()){
            scanner.startScan(Arrays.asList(scanFilter), scanSettings, scanCallback);
        } else {
            Toast.makeText(this, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void onScanQrClicked() {
        progressBar.setVisibility(View.GONE);
        if (PermissionUtils.hasCameraPermission(this)) {
            Intent intent = new Intent(BleClientActivity.this, ScannerActivity.class);
            startActivityForResult(intent, REQUEST_CODE_CAMERA_ACTIVITY);
        } else {
            PermissionUtils.requestCameraPermission(this, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    private void qrScanCompleted(String qrData) {
        bleClientManager = new BleClientManager();
        if (!bleClientManager.initBleClientManager(this, this, qrData)) {
            Toast.makeText(this, "Invalid Address from QR", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("+_", "Ble Client Manager init from QR Scan");
        }
    }

    private void decoupleDevice() {
        if (bleClientManager != null) {
            bleClientManager.destroyInstance();
        }
        isConnected = false;
        selectedDeviceName = "";
        selectedDeviceAddress = "";

        adapter.clearScannedDevices();
        foundMacs.clear();
        btnScanBle.setText(getString(R.string.scan));
        btnScanBle.setBackgroundColor(ContextCompat.getColor(BleClientActivity.this, android.R.color.holo_green_dark));
        tvSelectedDevice.setVisibility(View.GONE);
        btnScanQr.setVisibility(View.VISIBLE);
        rvScanResults.setVisibility(View.VISIBLE);
        etClientMessage.setVisibility(View.GONE);
    }

    public String formatMessageForSending(String message) {
        String id        = BluetoothAdapter.getDefaultAdapter().getName();
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis());
        String output    = "User: [" + id + "] at [" + timeStamp + "]\n\t";
        output += message.replaceAll("\n", "\n\t") + '\n';
        return output;
    }

    public void writeStringToServer(final String dataString) {
        if (dataString == null || dataString.isEmpty() || !isConnected) {
            Log.d("+_", "Didnt write string to server cause something was null/not connected");
            return;
        }
        writeBytesToServer(dataString.getBytes(StandardCharsets.US_ASCII));
    }

    public void writeBytesToServer(byte[] data) {
        if (data == null || data.length == 0 || !isConnected) {
            Log.d("+_", "Didnt write string to server cause something was null/not connected");
            return;
        }
        Log.d("+_", "In Write bytes to server");
        int index      = 0;
        int dataLength = data.length;
        while (dataLength - index > 20) {
            Log.d("+_", "In Write loop");
            byte[] chunk = Arrays.copyOfRange(data, index, index += 20);
            queuePublishSubject.onNext(chunk);
        }
        Log.d("+_", "Finished write loop");
        byte[] lastBytes = Arrays.copyOfRange(data, index, data.length);
        queuePublishSubject.onNext(lastBytes);
    }

    public void resetScanTimeoutWatchdog() {
        final int numEntries = scanResults.size();
        scanWatchdogHandler.removeCallbacksAndMessages(null);
        scanWatchdogHandler.postDelayed(() -> {
            Log.d("_+_", "Num Entries: " + numEntries + "ScanResults Size: " + scanResults.size());
            if (numEntries >= scanResults.size()) {
                scanner.stopScan(scanCallback);
                progressBar.setVisibility(View.GONE);
            }
        }, SCAN_TIMEOUT);
    }

    public void updateMessageDisplay(byte[] incomingData) {
        String messageText    = tvMessages.getText().toString();
        String incomingString = new String(incomingData, StandardCharsets.US_ASCII);
        messageText += incomingString;
        Log.d("+_", "Updated messages: " + incomingString);
        tvMessages.setText(messageText);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(scanner != null && scanCallback != null){
            scanner.stopScan(scanCallback);
        }
        decoupleDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SHOW_BLUETOOTH_SETTINGS) {
            Log.d("+_", "Returned from bluetooth settings page");
        } else if (requestCode == REQUEST_CODE_CAMERA_ACTIVITY) {
            boolean success = false;
            if (resultCode == RESULT_OK) {
                Bundle bundle = data.getExtras();
                if (bundle != null) {
                    String qrData = bundle.getString("QR_DATA", "");
                    if (!qrData.isEmpty()) {
                        success = true;
                        qrScanCompleted(qrData);
                    }
                } else {
                    Log.d("+_", "Bundle was null");
                }
            }
            if (!success) {
                Toast.makeText(this, "QR Scanning Error", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
                onScanQrClicked();
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        } else {
            if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
                if (PermissionUtils.canRequestCameraPermission(this)) {
                    PermissionUtils.requestCameraPermission(this, requestCode);
                } else {
                    PermissionUtils.showPermissionsSettings(this);
                }
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    @Override
    public void scanSelected(ScanResult scanResult) {
        selectedDeviceAddress = scanResult.getDevice().getAddress();
        selectedDeviceName = scanResult.getDevice().getName();

        bleClientManager = new BleClientManager();
        bleClientManager.initBleClientManager(this, this, selectedDeviceAddress);
    }

    private void showInvalidConnectionToast() {
        Toast.makeText(BleClientActivity.this, "Invalid BLE Connection", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void deviceConnected(boolean success) {
        if (success) {
            runOnUiThread(() -> {
                Log.d("+_", "Device Connected Callback: " + success);
                if(selectedDeviceName == null || selectedDeviceName.isEmpty()){
                    tvSelectedDevice.setText(selectedDeviceAddress);
                } else {
                    tvSelectedDevice.setText(selectedDeviceName);
                }
                tvSelectedDevice.setVisibility(View.VISIBLE);
                rvScanResults.setVisibility(View.GONE);
                etClientMessage.setVisibility(View.VISIBLE);
                btnScanBle.setText(R.string.uncouple_device);
                btnScanBle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                isConnected = true;
            });
        } else {
            Log.d("+_", "Device Connected callback: " + success);
        }
    }

    @Override
    public void onServerMessageReceived(byte[] data) {
        runOnUiThread(() -> {
            updateMessageDisplay(data);
        });
    }
}
