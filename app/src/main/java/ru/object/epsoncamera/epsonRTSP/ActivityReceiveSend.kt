package ru.`object`.epsoncamera.epsonRTSP

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.koin.android.viewmodel.ext.android.viewModel
import ru.`object`.epsoncamera.epsonLocal.R
import ru.`object`.epsoncamera.epsonRTSP.live.ReceiveViewModel

class ActivityReceiveSend : AppCompatActivity() {


    private val TAG = this.javaClass.simpleName

    private var mTextView_framerate: TextView? = null

    private var mTextView_captureState: TextView? = null
    private var mTextView_test: TextView? = null

    /* fun getOverlayview():ResultOverlayView{
         return overlayView
     }*/

    private val liveViewModel: ReceiveViewModel by viewModel()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epson_receive_send)

        Log.d(TAG, "ARSARS " + liveViewModel)


        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape

        } else {
            val navView = findViewById<BottomNavigationView>(R.id.nav_view)
            val navController = findNavController(R.id.nav_epson_host_fragment)
            navView.setupWithNavController(navController)
        }
        ActivityReceiveSend.mContext = this
        ActivityReceiveSend.instance = this
/*
            overlayView = binding.resultOverlay!!
*/
        // mTextView_framerate = binding.textViewFramerate

    }

    public override fun onStart() {
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


    companion object {
        private lateinit var instance: ActivityReceiveSend
        private lateinit var mContext: Context

    }
}