import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pedro.rtsp.utils.ConnectCheckerRtsp
import com.pedro.rtspserver.RtspServerCamera1
import com.pedro.sample.R
import com.pedro.sample.databinding.FragmentSendBinding
import java.io.File

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        binding = FragmentSendBinding.inflate(inflater, container, false)

        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.bStartStop.setOnClickListener(this)

        rtspServerCamera1 = RtspServerCamera1(binding.surfaceView, this, 1935)
        binding.surfaceView.holder.addCallback(this)
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
                    Toast.makeText(
                        requireContext(),
                        "Error preparing stream, This device cant do it",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            } else {
                binding.bStartStop.setText(R.string.start_button)
                rtspServerCamera1.stopStream()
                binding.tvUrl.text = ""
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
                Toast.makeText(
                    requireContext(),
                    "file " + currentDateAndTime + ".mp4 saved in " + folder.absolutePath,
                    Toast.LENGTH_SHORT
                ).show()
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
