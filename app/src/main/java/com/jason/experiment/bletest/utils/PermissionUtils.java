package com.jason.experiment.bletest.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;

/**
 * PermissionUtils
 * Created by jason on 18/6/18.
 */
public class PermissionUtils {


    public static boolean hasCameraPermission(Context context){
        if(Build.VERSION.SDK_INT < 23){
            return PermissionChecker.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean hasLocationPermission(Context context){
        if(Build.VERSION.SDK_INT < 23){
            return PermissionChecker.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean canRequestCameraPermission(AppCompatActivity context) {
        return Build.VERSION.SDK_INT < 23
               || ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.CAMERA);
    }

    public static boolean canRequestLocationPermission(AppCompatActivity context) {
        return Build.VERSION.SDK_INT < 23
               || ActivityCompat.shouldShowRequestPermissionRationale(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static void requestLocationPermission(AppCompatActivity context, int requestCode) {
        String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION};
        ActivityCompat.requestPermissions(context, permissions, requestCode);
    }

    public static void requestCameraPermission(AppCompatActivity context, int requestCode) {
        String[] permissions = {Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(context, permissions, requestCode);
    }

    public static void showPermissionsSettings(AppCompatActivity context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }


}
