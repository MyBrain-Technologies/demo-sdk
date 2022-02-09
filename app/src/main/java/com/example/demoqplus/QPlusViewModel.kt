package com.example.demoqplus

import android.content.Context
import androidx.lifecycle.ViewModel
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.model.EnumMBTDevice

class QPlusViewModel : ViewModel() {

    var mbtClient : MbtClient? = null

    fun init(context: Context, deviceType: EnumMBTDevice) {
        mbtClient = MbtClientManager.getMbtClient(context, deviceType)
    }
}