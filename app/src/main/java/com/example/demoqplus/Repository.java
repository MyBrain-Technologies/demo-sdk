package com.example.demoqplus;

import com.mybraintech.sdk.core.model.MbtDevice;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

public class Repository {
    private static final Repository INSTANCE = new Repository();

    private final MediatorLiveData<List<MbtDevice>> mDevices = new MediatorLiveData<>();

    private Repository() {}

    public static Repository instance() {
        return INSTANCE;
    }

    public LiveData<List<MbtDevice>> getDevices() {
        return mDevices;
    }

    public void addDeviceSource(LiveData<List<MbtDevice>> devices) {
        mDevices.addSource(devices, mDevices::setValue);
    }

    public void removeDeviceSource(LiveData<List<MbtDevice>> devices) {
        mDevices.removeSource(devices);
    }

}
