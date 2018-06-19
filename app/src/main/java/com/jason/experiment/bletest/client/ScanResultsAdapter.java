package com.jason.experiment.bletest.client;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jason.experiment.bletest.R;

import java.lang.ref.WeakReference;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.ScanResult;

/**
 * ScanResultsAdapter
 * Created by jason on 14/6/18.
 */
public class ScanResultsAdapter extends RecyclerView.Adapter<ScanResultsAdapter.ScanResultsViewHolder> {
    private List<ScanResult> results;
    private WeakReference<ScanSelectedCallback>                        callbackWeakReference;

    public ScanResultsAdapter(List<ScanResult> entries, ScanSelectedCallback callback) {
        callbackWeakReference = new WeakReference<>(callback);
        results = entries;
    }

    @NonNull
    @Override
    public ScanResultsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_client_list, parent, false);
        return new ScanResultsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanResultsViewHolder holder, int position) {
        final ScanResult currentEntry = results.get(position);
        holder.setScanResult(currentEntry);
        holder.view.setOnClickListener(v -> {
            if(callbackWeakReference != null && callbackWeakReference.get() != null){
                callbackWeakReference.get().scanSelected(currentEntry);
            }
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void addScannedDevice(ScanResult scanResult){
        results.add(scanResult);
        notifyDataSetChanged();
    }

    public void removeScannedDevice(ScanResult scanResult){
        results.remove(scanResult);
        notifyDataSetChanged();
    }

    public void clearScannedDevices(){
        results.clear();
        notifyDataSetChanged();
    }

    public interface ScanSelectedCallback{
        void scanSelected(ScanResult scanResult);
    }

    public static class ScanResultsViewHolder extends RecyclerView.ViewHolder {
        public final View     view;
        public       TextView tvName;
        public       TextView tvAddress;

        public ScanResultsViewHolder(View view) {
            super(view);
            this.view = view;
            tvName = view.findViewById(R.id.tv_card_client_name);
            tvAddress = view.findViewById(R.id.tv_card_client_addr);
        }

        public void setScanResult(ScanResult scanResult) {
            String nameText = "Name: " + scanResult.getDevice().getName();
            String addrText = "Addr: " + scanResult.getDevice().getAddress();
            tvName.setText(nameText);
            tvAddress.setText(addrText);
        }
    }
}
