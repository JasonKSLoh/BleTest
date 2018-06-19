package com.jason.experiment.bletest.client.qr_scanner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.jason.experiment.bletest.R;

/**
 * ScannerActivity
 * Created by jason on 18/6/18.
 */
public class ScannerActivity extends AppCompatActivity {
    CameraSource        cameraSource;
    BarcodeDetector     barcodeDetector;
    CameraSourcePreview svCamera;
    ConstraintLayout    layoutScannerParent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        svCamera = findViewById(R.id.sv_camera);
        layoutScannerParent = findViewById(R.id.layout_scanner_parent);
        initCamera();
    }

    @SuppressLint("MissingPermission")
    private void initCamera() {
        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        Detector.Processor<Barcode> processor = new Detector.Processor<Barcode>() {
            @Override
            public void release() {

            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() > 0) {
                    String qrData = barcodes.valueAt(0).displayValue;
                    Intent intent = new Intent();
                    Log.d("+_", "Got Address: " + qrData);
                    intent.putExtra("QR_DATA", qrData);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        };

        barcodeDetector.setProcessor(processor);

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setAutoFocusEnabled(true)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedFps(48.0f)
                .setRequestedPreviewSize(960, 960)
                .build();

//        svCamera.start(cameraSource);
    }

    @Override
    public void onPause() {
        if (cameraSource != null) {
            svCamera.stop();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (cameraSource != null) {
            svCamera.release();
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        svCamera.start(cameraSource);
        super.onResume();
    }

}
