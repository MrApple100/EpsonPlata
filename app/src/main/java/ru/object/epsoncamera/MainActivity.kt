package ru.`object`.epsoncamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import ru.`object`.epsoncamera.epsonRTSP.ActivityReceiveSend
import ru.`object`.epsoncamera.epsonLocal.MoverioCameraSampleFragment
import ru.`object`.epsoncamera.epsonLocal.R

class MainActivity : Activity() {

    private val PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private lateinit var b_Epson_Rtsp: Button
    private lateinit var b_Epson_Local: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        b_Epson_Rtsp = findViewById(R.id.b_Epson_Rtsp)
        b_Epson_Rtsp.setOnClickListener {
            if (!hasPermissions(this, *PERMISSIONS)) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
            } else {
                Toast.makeText(this, "Start ActivityReceiveSend", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, ActivityReceiveSend::class.java))
            }
        }
        b_Epson_Local = findViewById(R.id.b_Epson_Local)
        b_Epson_Local.setOnClickListener {
                startActivity(Intent(this, MoverioCameraSampleFragment::class.java))
        }
    }

    private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context,
                        permission) != PackageManager.PERMISSION_GRANTED) {
                    println("Perm "+permission+" "+"false")
                    return false
                }
                println("Perm "+permission+" "+"true             ")

            }
        }
        return true
    }
}