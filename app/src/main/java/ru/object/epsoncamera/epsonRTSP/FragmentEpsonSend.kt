package ru.`object`.epsoncamera.epsonRTSP

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import org.koin.android.viewmodel.ext.android.viewModel
import ru.`object`.epsoncamera.epsonLocal.R
import ru.`object`.epsoncamera.epsonLocal.databinding.FragmentEpsonSendBinding
import ru.`object`.epsoncamera.epsonRTSP.live.ReceiveViewModel
import java.io.File

class FragmentEpsonSend : Fragment(), ConnectCheckerRtsp, View.OnClickListener,
    SurfaceHolder.Callback {

    private lateinit var rtspServerEpson: RtspServerEpson
//  private lateinit var button: Button
    // private lateinit var bRecord: Button
    // private lateinit var switch_camera: Button
//  private lateinit var surfaceView: SurfaceView
//  private lateinit var tv_url:TextView

    private lateinit var binding: FragmentEpsonSendBinding

    private val liveViewModel: ReceiveViewModel by viewModel()


    private var currentDateAndTime = ""
    private lateinit var folder: File

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        Log.d("FragmentEpsonSend", "FESFES " + liveViewModel)

        binding = FragmentEpsonSendBinding.inflate(inflater, container, false)

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.bStartStop.setOnClickListener(this)

        rtspServerEpson = RtspServerEpson(binding.surfaceView, this, 1935)
        binding.surfaceView.holder.addCallback(this)
        liveViewModel.overlayView.value = binding.overlay
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        rtspServerEpson.startPreview()

    }

    override fun onResume() {
        super.onResume()
        rtspServerEpson.startPreview()

    }

    override fun onStop() {
        super.onStop()
        rtspServerEpson.stopPreview()
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
            rtspServerEpson.stopStream()
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
            rtspServerEpson.stopStream()
            binding.bStartStop.setText(R.string.start_button)
            binding.tvUrl.text = ""
        }
    }

    override fun onAuthSuccessRtsp() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "Auth success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.b_start_stop -> if (!rtspServerEpson.isStreaming) {
                if (rtspServerEpson.prepareVideo()) {
                    binding.bStartStop.setText(R.string.stop_button)
                    rtspServerEpson.startStream()
                    binding.tvUrl.text = rtspServerEpson.getEndPointConnection()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Error preparing stream, This device cant do it",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            } else {
                binding.bStartStop.setText(R.string.start_button)
                rtspServerEpson.stopStream()
                binding.tvUrl.text = ""
            }

            else -> {
            }
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
        rtspServerEpson.startPreview()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        //comment all to dont destroy when slide to client

        if (rtspServerEpson.isStreaming) {
            rtspServerEpson.stopStream()
            binding.bStartStop.text = resources.getString(R.string.start_button)
            binding.tvUrl.text = ""
        }
        rtspServerEpson.stopPreview()
    }
}
