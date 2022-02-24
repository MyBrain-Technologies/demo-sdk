package com.example.demoqplus;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.mybraintech.sdk.core.model.MbtDevice;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import timber.log.Timber;

public class DeviceRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<MbtDevice> devices;

    // data is passed into the constructor
    public DeviceRecyclerViewAdapter() {
        this.devices = new ArrayList<MbtDevice>();
    }

    // inflates the row layout from xml when needed
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View rootView = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_list_item, parent, false);
        return new RecyclerViewViewHolder(rootView);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        String deviceName = devices.get(position).toString();
        RecyclerViewViewHolder viewHolder = (RecyclerViewViewHolder) holder;
        viewHolder.myTextView.setText(deviceName);
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void updateDeviceList(final List<MbtDevice> devices){
        this.devices.clear();
        this.devices = devices;
        notifyDataSetChanged();
    }

    // stores and recycles views as they are scrolled off screen
    public class RecyclerViewViewHolder extends RecyclerView.ViewHolder  {
        TextView myTextView;

        RecyclerViewViewHolder(View itemView) {
            super(itemView);
            myTextView = itemView.findViewById(R.id.fragment_device_name);
            String name = getDevice(getAdapterPosition()).toString();
            Timber.d("name in adapter is: "+ name);
        }
    }

    // convenience method for getting data at click position
    public MbtDevice getDevice(int id) {
        return devices.get(id);
    }
}
