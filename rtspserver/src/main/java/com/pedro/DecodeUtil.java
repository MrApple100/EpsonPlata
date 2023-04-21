package com.pedro;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class DecodeUtil {
    private static final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;

        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    public static byte[] integersToBytes(int[] values) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (int i = 0; i < values.length; ++i) {
            dos.writeInt(values[i]);
        }

        return baos.toByteArray();
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    public static void decodeYUV420SP(int[] rgb, byte[] yy, byte[] uu, byte[] vv, int width, int height) {
        final int frameSize = width * height;

        for (int yp = 0; yp < frameSize; yp++) {
            int u = 0, v = 0;
            int y = yy[yp];//  int y = (0xff & ((int) yuv420sp[yp])) - 16;
            if (y < 0)
                y = 0;
            v = vv[yp/4]-128;
            u = uu[yp/4]-128;

           // System.out.println(y + " " + v + " " + " "+ u);



            int y1192 = 1192 * y;
            int r = (y1192 + 1634 * v);
            int g = (y1192 - 833 * v - 400 * u);
            int b = (y1192 + 2066 * u);

         //   System.out.println(r + " " + g + " " + " "+ b);


            if (r < 0) r = 0;
            else if (r > 262143) r = 262143;
            if (g < 0) g = 0;
            else if (g > 262143) g = 262143;
            if (b < 0) b = 0;
            else if (b > 262143) b = 262143;

            rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);

        }
    }

   /* //Method from Ketai project! Not mine! See below...
    public static void decodeYUV420SP(int[] rgb, byte[] yy, byte[] uu, byte[] vv, int width, int height) {

        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0)
                    y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

*/

    /*AVCodec avcodec = new AVCodec();
    AVCodecContext videoCodecContext = new AVCodecContext();

    public IplImage decodeFromVideo(byte[] data, long timeStamp) {
        avcodec.av_init_packet(reveivedVideoPacket); // Empty AVPacket
        *//*
         * Determine if the frame is a Data Frame or Key. IFrame 1 = PFrame 0 = Key
         * Frame
         *//*
        byte frameFlag = data[1];
        byte[] subData = Arrays.copyOfRange(data, 5, data.length);

        BytePointer videoData = new BytePointer(subData);
        if (frameFlag == 0) {
            avcodec.AVCodec codec = avcodec
                    .avcodec_find_decoder(avcodec.AV_CODEC_ID_H264);
            if (codec != null) {
                videoCodecContext = null;
                videoCodecContext = avcodec.avcodec_alloc_context3(codec);
                videoCodecContext.width(320);
                videoCodecContext.height(240);
                videoCodecContext.pix_fmt(avutil.AV_PIX_FMT_YUV420P);
                videoCodecContext.codec_type(avutil.AVMEDIA_TYPE_VIDEO);
                videoCodecContext.extradata(videoData);
                videoCodecContext.extradata_size(videoData.capacity());

                videoCodecContext.flags2(videoCodecContext.flags2()
                        | avcodec.CODEC_FLAG2_CHUNKS);
                avcodec.avcodec_open2(videoCodecContext, codec,
                        (PointerPointer) null);

                if ((videoCodecContext.time_base().num() > 1000)
                        && (videoCodecContext.time_base().den() == 1)) {
                    videoCodecContext.time_base().den(1000);
                }
            } else {
                Log.e("test", "Codec could not be opened");
            }
        }

        if ((decodedPicture = avcodec.avcodec_alloc_frame()) != null) {
            if ((processedPicture = avcodec.avcodec_alloc_frame()) != null) {
                int width = getImageWidth() > 0 ? getImageWidth()
                        : videoCodecContext.width();
                int height = getImageHeight() > 0 ? getImageHeight()
                        : videoCodecContext.height();

                switch (imageMode) {
                    case COLOR:
                    case GRAY:
                        int fmt = 3;
                        int size = avcodec.avpicture_get_size(fmt, width, height);
                        processPictureBuffer = new BytePointer(
                                avutil.av_malloc(size));
                        avcodec.avpicture_fill(new AVPicture(processedPicture),
                                processPictureBuffer, fmt, width, height);
                        returnImageFrame = opencv_core.IplImage.createHeader(320,
                                240, 8, 1);
                        break;
                    case RAW:
                        processPictureBuffer = null;
                        returnImageFrame = opencv_core.IplImage.createHeader(320,
                                240, 8, 1);
                        break;
                    default:
                        Log.d("showit",
                                "At default of swith case 1.$SwitchMap$com$googlecode$javacv$FrameGrabber$ImageMode[ imageMode.ordinal()]");
                }

                reveivedVideoPacket.data(videoData);
                reveivedVideoPacket.size(videoData.capacity());

                reveivedVideoPacket.pts(timeStamp);
                videoCodecContext.pix_fmt(avutil.AV_PIX_FMT_YUV420P);
                decodedFrameLength = avcodec.avcodec_decode_video2(videoCodecContext,
                        decodedPicture, isVideoDecoded, reveivedVideoPacket);

                if ((decodedFrameLength >= 0) && (isVideoDecoded[0] != 0)) {
 .... Process image same as javacv .....
                }*/
}
