package com.jason.experiment.bletest.utils;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/**
 * QrManager
 * Created by jason on 18/6/18.
 */
public class QrManager {

    private static final int DEFAULT_HEIGHT = 400;
    private static final int DEFAULT_WIDTH  = 400;

    public static Bitmap getQrCode(String data) {
        BarcodeEncoder encoder = new BarcodeEncoder();
        try {
            return encoder.encodeBitmap(data, BarcodeFormat.QR_CODE, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        } catch (WriterException e) {
            return null;
        }
    }

    public static Bitmap getQrCode(String data, int height, int width) {
        BarcodeEncoder encoder = new BarcodeEncoder();
        try {
            return encoder.encodeBitmap(data, BarcodeFormat.QR_CODE, width, height);
        } catch (WriterException e) {
            return null;
        }
    }

}
