package com.jason.experiment.bletest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

import com.jason.experiment.bletest.client.BleClientActivity;
import com.jason.experiment.bletest.server.BleServerActivity;
import com.jason.experiment.bletest.utils.PermissionUtils;

public class MainActivity extends AppCompatActivity {

    ImageView ivServer;
    ImageView ivClient;

    int clientRequestCode = 2234;
    int serverRequestCode = 2233;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ivServer = findViewById(R.id.iv_server);
        ivClient = findViewById(R.id.iv_client);

        ivServer.setOnClickListener(v -> {
            onServerButtonClicked();
        });

        ivClient.setOnClickListener(v -> {
            onClientButtonClicked();
        });
    }

    private void onClientButtonClicked() {
        if (PermissionUtils.hasLocationPermission(this)) {
            Intent i = new Intent(MainActivity.this, BleClientActivity.class);
            startActivity(i);
        } else {
            PermissionUtils.requestLocationPermission(this, clientRequestCode);
        }
    }

    private void onServerButtonClicked() {
        if (PermissionUtils.hasLocationPermission(this)) {
            Intent i = new Intent(MainActivity.this, BleServerActivity.class);
            startActivity(i);
        } else {
//            requestPermissions(clientRequestCode);
            if (PermissionUtils.canRequestLocationPermission(this)) {
                PermissionUtils.requestLocationPermission(this, clientRequestCode);
            } else {
                PermissionUtils.showPermissionsSettings(this);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == clientRequestCode) {
                onClientButtonClicked();
            } else if (requestCode == serverRequestCode) {
                onServerButtonClicked();
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        } else {
            if(requestCode == clientRequestCode || requestCode == serverRequestCode){
                if (PermissionUtils.canRequestLocationPermission(this)) {
                    PermissionUtils.requestLocationPermission(this, requestCode);
                } else {
                    PermissionUtils.showPermissionsSettings(this);
                }
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }
}
