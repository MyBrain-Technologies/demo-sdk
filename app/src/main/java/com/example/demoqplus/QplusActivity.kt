package com.example.demoqplus

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import engine.MbtClient

class QplusActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        MbtClient.init(this)
        TODO("implement sdk functions")
    }
}