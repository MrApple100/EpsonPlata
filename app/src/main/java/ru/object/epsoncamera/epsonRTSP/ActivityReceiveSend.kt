package ru.`object`.epsoncamera.epsonRTSP

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.`object`.epsoncamera.epsonLocal.R

class ActivityReceiveSend : AppCompatActivity(){

    private val TAG = this.javaClass.simpleName

    private var mTextView_framerate: TextView? = null

    private var mTextView_captureState: TextView? = null
    private var mTextView_test: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epson_receive_send)

        val orientation = resources.configuration.orientation


        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape

        } else {
            val navView = findViewById<BottomNavigationView>(R.id.nav_view)
            val navController = findNavController(R.id.nav_epson_host_fragment)
            navView.setupWithNavController(navController)
        }

        ActivityReceiveSend.mContext=this
        ActivityReceiveSend.instance=this
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