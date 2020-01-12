package com.panda.redpacket

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        var onOff = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startService(Intent(this, RedPacketService::class.java))

        btOnOff.setOnClickListener {
            onOff = !onOff
            if (onOff) {
                btOnOff.text = "开"
            } else {
                btOnOff.text = "关"
            }
        }

        btWza.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }


    }
}