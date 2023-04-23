package ru.`object`.epsoncamera.epsonRTSP

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.epson.moverio.hardware.camera.*
import com.epson.moverio.util.PermissionGrantResultCallback
import com.epson.moverio.util.PermissionHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.pedro.sample.R
import com.pedro.sample.databinding.FragmentReceiveBinding
import com.pedro.sample.databinding.FragmentSendBinding
import java.util.*

class ActivityReceiveSend : AppCompatActivity(), PermissionGrantResultCallback {

    private val TAG = this.javaClass.simpleName

    private var mTextView_framerate: TextView? = null

    private var mTextView_captureState: TextView? = null
    private var mTextView_test: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive_send)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)

        val orientation = resources.configuration.orientation
        val bindingSend:FragmentSendBinding = DataBindingUtil.setContentView(instance,R.layout.fragment_send)
        val bindingReceive: FragmentReceiveBinding = DataBindingUtil.setContentView(instance,R.layout.fragment_receive)


        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape

        } else {
            val navController = findNavController(R.id.nav_host_fragment)
            navView.setupWithNavController(navController)
        }

        ActivityReceiveSend.mContext=this
        ActivityReceiveSend.instance=this
       // mTextView_framerate = binding.textViewFramerate

    }

    override fun onStart() {
        super.onStart()


    }
    public override fun onResume() {
        super.onResume()
        //  Toast.makeText(mContext,"Resume",Toast.LENGTH_SHORT).show();
    }

    public override fun onPause() {
        super.onPause()
        //   Toast.makeText(mContext,"Pause",Toast.LENGTH_SHORT).show();
    }

    public override fun onStop() {
        super.onStop()

    }

    override fun onPermissionGrantResult(permission: String, grantResult: Int) {
        Snackbar.make(
            window.decorView,
            permission + " is " + if (PermissionHelper.PERMISSION_GRANTED == grantResult) "GRANTED" else "DENIED",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    companion object {
        private lateinit var instance: ActivityReceiveSend
        private lateinit var mContext: Context

    }
}