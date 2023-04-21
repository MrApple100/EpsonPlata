package com.pedro.rtmp.rtmp

import android.util.Log
import com.pedro.rtmp.amf.v0.AmfEcmaArray
import com.pedro.rtmp.amf.v0.AmfNull
import com.pedro.rtmp.amf.v0.AmfObject
import com.pedro.rtmp.amf.v0.AmfString
import com.pedro.rtmp.rtmp.chunk.ChunkStreamId
import com.pedro.rtmp.rtmp.chunk.ChunkType
import com.pedro.rtmp.rtmp.message.BasicHeader
import com.pedro.rtmp.rtmp.message.command.CommandAmf0
import com.pedro.rtmp.rtmp.message.data.DataAmf0
import java.io.OutputStream

class CommandsManagerAmf0: CommandsManager() {
  override fun sendConnect(auth: String, output: OutputStream) {
    val connect = CommandAmf0("connect", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    val connectInfo = AmfObject()
    connectInfo.setProperty("app", appName + auth)
    connectInfo.setProperty("flashVer", "FMLE/3.0 (compatible; Lavf57.56.101)")
    connectInfo.setProperty("swfUrl", "")
    connectInfo.setProperty("tcUrl", tcUrl + auth)
    connectInfo.setProperty("fpad", false)
    connectInfo.setProperty("capabilities", 239.0)
    if (!audioDisabled) {
      connectInfo.setProperty("audioCodecs", 3191.0)
    }
    if (!videoDisabled) {
      connectInfo.setProperty("videoCodecs", 252.0)
      connectInfo.setProperty("videoFunction", 1.0)
    }
    connectInfo.setProperty("pageUrl", "")
    connectInfo.setProperty("objectEncoding", 0.0)
    connect.addData(connectInfo)

    connect.writeHeader(output)
    connect.writeBody(output)
    sessionHistory.setPacket(commandId, "connect")
    Log.i(TAG, "send $connect")
  }

  override fun createStream(output: OutputStream) {
    val releaseStream = CommandAmf0("releaseStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    releaseStream.addData(AmfNull())
    releaseStream.addData(AmfString(streamName))

    releaseStream.writeHeader(output)
    releaseStream.writeBody(output)
    sessionHistory.setPacket(commandId, "releaseStream")
    Log.i(TAG, "send $releaseStream")

    val fcPublish = CommandAmf0("FCPublish", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    fcPublish.addData(AmfNull())
    fcPublish.addData(AmfString(streamName))

    fcPublish.writeHeader(output)
    fcPublish.writeBody(output)
    sessionHistory.setPacket(commandId, "FCPublish")
    Log.i(TAG, "send $fcPublish")

    val createStream = CommandAmf0("createStream", ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_CONNECTION.mark))
    createStream.addData(AmfNull())

    createStream.writeHeader(output)
    createStream.writeBody(output)
    sessionHistory.setPacket(commandId, "createStream")
    Log.i(TAG, "send $createStream")
  }

  override fun sendMetadata(output: OutputStream) {
    val name = "@setDataFrame"
    val metadata = DataAmf0(name, getCurrentTimestamp(), streamId)
    metadata.addData(AmfString("onMetaData"))
    val amfEcmaArray = AmfEcmaArray()
    amfEcmaArray.setProperty("duration", 0.0)
    if (!videoDisabled) {
      amfEcmaArray.setProperty("width", width.toDouble())
      amfEcmaArray.setProperty("height", height.toDouble())
      amfEcmaArray.setProperty("videocodecid", 7.0)
      amfEcmaArray.setProperty("framerate", fps.toDouble())
      amfEcmaArray.setProperty("videodatarate", 0.0)
    }
    if (!audioDisabled) {
      amfEcmaArray.setProperty("audiocodecid", 10.0)
      amfEcmaArray.setProperty("audiosamplerate", sampleRate.toDouble())
      amfEcmaArray.setProperty("audiosamplesize", 16.0)
      amfEcmaArray.setProperty("audiodatarate", 0.0)
      amfEcmaArray.setProperty("stereo", isStereo)
    }
    amfEcmaArray.setProperty("filesize", 0.0)
    metadata.addData(amfEcmaArray)

    metadata.writeHeader(output)
    metadata.writeBody(output)
    Log.i(TAG, "send $metadata")
  }

  override fun sendPublish(output: OutputStream) {
    val name = "publish"
    val publish = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId,
        BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    publish.addData(AmfNull())
    publish.addData(AmfString(streamName))
    publish.addData(AmfString("live"))

    publish.writeHeader(output)
    publish.writeBody(output)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $publish")
  }

  override fun sendClose(output: OutputStream) {
    val name = "closeStream"
    val closeStream = CommandAmf0(name, ++commandId, getCurrentTimestamp(), streamId, BasicHeader(ChunkType.TYPE_0, ChunkStreamId.OVER_STREAM.mark))
    closeStream.addData(AmfNull())

    closeStream.writeHeader(output)
    closeStream.writeBody(output)
    sessionHistory.setPacket(commandId, name)
    Log.i(TAG, "send $closeStream")
  }
}