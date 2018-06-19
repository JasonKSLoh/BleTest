package com.jason.experiment.bletest.server;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jason.experiment.bletest.R;

import java.util.Iterator;
import java.util.SortedSet;

/**
 * ClientListAdapter
 * Created by jason on 14/6/18.
 */
public class ClientListAdapter extends RecyclerView.Adapter<ClientListAdapter.ClientListViewHolder> {
    private SortedSet<BluetoothDevice> deviceSet;

    public ClientListAdapter (SortedSet<BluetoothDevice> entries) {
        deviceSet = entries;
    }

    @Override
    public ClientListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_client_list, parent, false);
        return new ClientListViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ClientListViewHolder holder, final int position) {
        final  BluetoothDevice currentEntry = getItemAtIndexFromSet(deviceSet, position);
        if(currentEntry == null){
            holder.setInfo("Error", "Error");
        } else {
            String name = currentEntry.getName();
            String addr = currentEntry.getAddress();
            holder.setInfo(name, addr);
        }
    }

    @Override
    public int getItemCount() {
        return deviceSet.size();
    }

    private BluetoothDevice getItemAtIndexFromSet(SortedSet<BluetoothDevice> sortedSet, int index){
        if(index < 0 || sortedSet == null || index > sortedSet.size()){
            return null;
        }
        Iterator<BluetoothDevice> iterator = sortedSet.iterator();
        int counter = 0;
        while(iterator.hasNext() && counter <= index){
            if(counter == index){
                return iterator.next();
            } else {
                iterator.next();
                counter++;
            }
        }
        return null;
    }

    public void addDevice(BluetoothDevice device){
        deviceSet.add(device);
        notifyDataSetChanged();
    }

    public void removeDevice(BluetoothDevice device){
        deviceSet.remove(device);
        notifyDataSetChanged();
    }

    public void clearDevices(){
        deviceSet.clear();
        notifyDataSetChanged();
    }


    public static class ClientListViewHolder extends RecyclerView.ViewHolder {
        public final View     view;
        public       TextView tvName;
        public       TextView tvAddress;

        public ClientListViewHolder(View view) {
            super(view);
            this.view = view;
            tvName = view.findViewById(R.id.tv_card_client_name);
            tvAddress = view.findViewById(R.id.tv_card_client_addr);
        }

        public void setInfo(String clientName, String clientAddr){
            String nameText = "Name: " + clientName;
            String addrText = "Addr: " + clientAddr;
            tvName.setText(nameText);
            tvAddress.setText(addrText);
        }
    }
}
