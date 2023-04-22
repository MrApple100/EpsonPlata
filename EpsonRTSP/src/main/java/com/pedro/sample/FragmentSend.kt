package com.pedro.sample

import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtspserver.RtspServerCamera1
import com.pedro.sample.databinding.FragmentSendBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FragmentSend : Fragment(), ConnectCheckerRtsp, View.OnClickListener,
    SurfaceHolder.Callback {

  private lateinit var rtspServerCamera1: RtspServerCamera1
//  private lateinit var button: Button
 // private lateinit var bRecord: Button
 // private lateinit var switch_camera: Button
//  private lateinit var surfaceView: SurfaceView
//  private lateinit var tv_url:TextView

  private lateinit var binding: FragmentSendBinding



  private var currentDateAndTime = ""
  private lateinit var folder: File

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {


    binding = FragmentSendBinding.inflate(inflater, container, false)

    requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
   // setContentView(R.layout.activity_camera_demo)
   // folder = File(getExternalFilesDir(null)!!.absolutePath + "/rtmp-rtsp-stream-client-java")
    //button = findViewById(R.id.b_start_stop)
    binding.bStartStop.setOnClickListener(this)
    //bRecord = findViewById(R.id.b_record)
    binding.bRecord.setOnClickListener(this)
    binding.switchCamera.setOnClickListener(this)
  //  surfaceView = findViewById(R.id.switch_camera)
    rtspServerCamera1 = RtspServerCamera1(binding.surfaceView, this, 1935)
    binding.surfaceView.holder.addCallback(this)
 //   tv_url = findViewById(R.id.tv_url);
    return binding.root
  }

  override fun onNewBitrateRtsp(bitrate: Long) {

  }

  override fun onConnectionSuccessRtsp() {
    requireActivity().runOnUiThread {
      Toast.makeText(requireContext(), "Connection success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onConnectionFailedRtsp(reason: String) {
   requireActivity().runOnUiThread {
      Toast.makeText(requireContext(), "Connection failed. $reason", Toast.LENGTH_SHORT)
          .show()
      rtspServerCamera1.stopStream()
      binding.bStartStop.setText(R.string.start_button)
    }
  }

  override fun onConnectionStartedRtsp(rtspUrl: String) {
  }

  override fun onDisconnectRtsp() {
   requireActivity().runOnUiThread {
      Toast.makeText(requireContext(), "Disconnected", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onAuthErrorRtsp() {
   requireActivity().runOnUiThread {
      Toast.makeText(requireContext(), "Auth error", Toast.LENGTH_SHORT).show()
      rtspServerCamera1.stopStream()
      binding.bStartStop.setText(R.string.start_button)
      binding.tvUrl.text = ""
    }
  }

  override fun onAuthSuccessRtsp() {
    activity!!.runOnUiThread {
      Toast.makeText(requireContext(), "Auth success", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onClick(view: View) {
    when (view.id) {
      R.id.b_start_stop -> if (!rtspServerCamera1.isStreaming) {
        if (rtspServerCamera1.isRecording || rtspServerCamera1.prepareAudio() && rtspServerCamera1.prepareVideo()) {
         binding.bStartStop.setText(R.string.stop_button)
          rtspServerCamera1.startStream()
         binding.tvUrl.text = rtspServerCamera1.getEndPointConnection()
        } else {
          Toast.makeText(requireContext(), "Error preparing stream, This device cant do it", Toast.LENGTH_SHORT)
              .show()
        }
      } else {
       binding.bStartStop.setText(R.string.start_button)
        rtspServerCamera1.stopStream()
       binding.tvUrl.text = ""
      }
      R.id.switch_camera -> try {
        rtspServerCamera1.switchCamera()
      } catch (e: CameraOpenException) {
        Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
      }

      R.id.b_record -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
          if (!rtspServerCamera1.isRecording) {
            try {
              if (!folder.exists()) {
                folder.mkdir()
              }
              val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
              currentDateAndTime = sdf.format(Date())
              if (!rtspServerCamera1.isStreaming) {
                if (rtspServerCamera1.prepareAudio() && rtspServerCamera1.prepareVideo()) {
                  rtspServerCamera1.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
                 binding.bRecord.setText(R.string.stop_record)
                  Toast.makeText(requireContext(), "Recording... ", Toast.LENGTH_SHORT).show()
                } else {
                  Toast.makeText(
                   requireContext(), "Error preparing stream, This device cant do it",
                    Toast.LENGTH_SHORT
                  ).show()
                }
              } else {
                rtspServerCamera1.startRecord(folder.absolutePath + "/" + currentDateAndTime + ".mp4")
               binding.bRecord.setText(R.string.stop_record)
                Toast.makeText(requireContext(), "Recording... ", Toast.LENGTH_SHORT).show()
              }
            } catch (e: IOException) {
              rtspServerCamera1.stopRecord()
             binding.bRecord.setText(R.string.start_record)
              Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
          } else {
            rtspServerCamera1.stopRecord()
           binding.bRecord.setText(R.string.start_record)
            Toast.makeText(
             requireContext(), "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
              Toast.LENGTH_SHORT
            ).show()
          }
        } else {
          Toast.makeText(requireContext(), "You need min JELLY_BEAN_MR2(API 18) for do it...", Toast.LENGTH_SHORT).show()
        }
      }
      else -> {
      }
    }
  }

  override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
  }

  override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
    rtspServerCamera1.startPreview()
  }

  override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
      //comment all to dont destroy when slide to client
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      if (rtspServerCamera1.isRecording) {
        rtspServerCamera1.stopRecord()
       binding.bRecord.setText(R.string.start_record)
        Toast.makeText(requireContext(), "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath, Toast.LENGTH_SHORT).show()
        currentDateAndTime = ""
      }
    }
    if (rtspServerCamera1.isStreaming) {
      rtspServerCamera1.stopStream()
     binding.bStartStop.text = resources.getString(R.string.start_button)
     binding.tvUrl.text = ""
    }
    rtspServerCamera1.stopPreview()
  }
}
