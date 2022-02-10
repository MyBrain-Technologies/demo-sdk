package com.example.demoqplus.simpleVersion;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothStateReceiver extends BroadcastReceiver {

    private boolean isActivated;

    public BluetoothStateReceiver(boolean state){
        this.isActivated = state;
    }

    public boolean isBluetoothOn(){
        return this.isActivated;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                case BluetoothAdapter.STATE_TURNING_OFF:
                    this.isActivated = false;
                    break;
                case BluetoothAdapter.STATE_ON:
                    this.isActivated = true;
                    break;
            }
        }
    }
}
