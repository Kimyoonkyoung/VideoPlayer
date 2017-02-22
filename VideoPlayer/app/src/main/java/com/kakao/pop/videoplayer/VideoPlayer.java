package com.kakao.pop.videoplayer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 일단 디코더를 다 켜놓고 ! 계속돌려놓고
 * 터치할때마다 뷰 바뀌는걸로 바꿔보기
 * 터치떼면 정지로
 */
public class VideoPlayer implements SensorEventListener {
    private static final String TAG = "VideoPlayer";

    private MediaExtractor mExtractor = null;
    private MediaCodec mMediaCodec = null;

    private int mInputBufIndex = 0;

    private boolean isForceStop = false;
    private volatile boolean isPause = false;

    protected VideoPlayerInterface mListener = null;

    private Surface mOutputSurface;

    private Context mContext;

    private SensorManager mSensorManager = null;
    private SensorEvent mEvent = null;
    private Sensor mGyro = null;

    public VideoPlayer(Surface outputSurface, Context context)
    {
        mOutputSurface = outputSurface;
        mState = State.Stopped;
        mContext = context;

        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, mGyro, SensorManager.SENSOR_DELAY_NORMAL);
    }

    int cnt = 0;
    @Override
    public void onSensorChanged(SensorEvent event) {
        this.mEvent = event;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                /*if (Math.abs(event.values[0]) > Math.abs(event.values[1])) { //x가 크고
                    if (event.values[0] > 0.f) {//오른쪽이면
                        int test = seekTime + 1;
                        if (test < totalSec) {
                          //  Log.d(TAG, "sensor seek time : " + test);
                        //    seekTo(test);
                        } else {
                        //    seekTo(totalSec);
                        }
                    }
                }*/
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //센서의 정확도가 변경되었을 때 진입하는 함수
        //일반적으로 잘 사용되지 않음
    }

    public void setOnAudioStreamInterface(VideoPlayerInterface listener)
    {
        this.mListener = listener;
    }

    public enum State
    {
        Stopped, Prepare, Buffering, Playing, Pause
    };

    State mState = State.Stopped;

    public State getState()
    {
        return mState;
    }

    private String mMediaPath;

    public void setUrlString(String mUrlString)
    {
        this.mMediaPath = mUrlString;
    }

    public void play() throws IOException
    {
        mState = State.Prepare;
        isForceStop = false;

        mVideoPlayerHandler.onVideoPlayerBuffering(VideoPlayer.this);

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                decodeLoop();
            }
        }).start();
    }

    private static int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }

        return -1;
    }

    double duration;
    int totalSec, min, sec;
    private void decodeLoop()
    {
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        MediaFormat format;
        String mime;
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(this.mMediaPath);
            int trackIndex = selectTrack(mExtractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + mMediaPath);
            }
            mExtractor.selectTrack(trackIndex);
            format = mExtractor.getTrackFormat(trackIndex);

            mime = format.getString(MediaFormat.KEY_MIME);
            mMediaCodec = MediaCodec.createDecoderByType(mime);
            mMediaCodec.configure(format, mOutputSurface, null, 0); //TODO : 여기서 두번째 파라미터로 surface 를 넘겨주기
            mMediaCodec.start();

            duration = format.getLong(MediaFormat.KEY_DURATION);
            totalSec = (int) (duration / 1000 / 1000);
            min = totalSec / 60;
            sec = totalSec % 60;

        } catch (Exception e) {
            mVideoPlayerHandler.onVideoPlayerError(VideoPlayer.this);
            return;
        }

        mVideoPlayerHandler.onVideoPlayerDuration(totalSec);

        Log.d(TAG, "Time = " + min + " : " + sec);
        Log.d(TAG, "Duration = " + duration);
        Log.i(TAG, "mime " + mime);

        codecInputBuffers = mMediaCodec.getInputBuffers();
        codecOutputBuffers = mMediaCodec.getOutputBuffers();

        mExtractor.selectTrack(0);

        final long kTimeOutUs = 10000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        int noOutputCounter = 0;
        int noOutputCounterLimit = 50;

        int frameCnt = 0;
        long t =0;
        while (!sawInputEOS && noOutputCounter < noOutputCounterLimit && !isForceStop) //TODO 내가 완전종료할때까지 걍 계속 돌면서, sensor가 정상이면 수행
        {
            if (!sawInputEOS)
            {
                if (isSeek)
                {
                    isPause = false;
                    //long t = seekTime * 1000 * 1000;  //TODO : frame count 로 들어오는 인풋을 시간으로 변경해주기 (전체시간*현재frame)/전체frame
                    frameCnt = seekTime;
                    t = (long)((duration * seekTime) / (totalSec * 30));
                    Log.d(TAG, "extractor Seek --> seekTime : " + seekTime + " totalSec : " + t);
                    mExtractor.seekTo(t, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    isSeek = false;
                }
                if(isPause)
                {
                    if(mState != State.Pause)
                    {
                        mState = State.Pause;
                        mVideoPlayerHandler.onVideoPlayerPause(); //TODO : pause
                    }
                    continue;
                }
                noOutputCounter++;

                mInputBufIndex = mMediaCodec.dequeueInputBuffer(kTimeOutUs);
                if (mInputBufIndex >= 0)
                {
                    ByteBuffer dstBuf = codecInputBuffers[mInputBufIndex];

                    int sampleSize = mExtractor.readSampleData(dstBuf, 0);
                    long presentationTimeUs = 0;

                    if (sampleSize < 0)
                    {
                        Log.d(TAG, "saw input EOS.");
                        sawInputEOS = true;
                        sampleSize = 0;
                    }
                    else
                    {
                        presentationTimeUs = mExtractor.getSampleTime();
                        Log.d(TAG, "extractor.getSampleTime() : " + presentationTimeUs);

                        frameCnt++;
                       // Log.d(TAG, "presentaionTime = " + frameCnt + " / " + totalSec*30 + " = " + (int)((frameCnt/(double)(totalSec*30.0))*(totalSec*30.0)));
                        mVideoPlayerHandler.onVideoPlayerCurrentTime((int)(frameCnt)); //여기서 progress 갱신됨
                    }

                    mMediaCodec.queueInputBuffer(mInputBufIndex, 0, sampleSize, presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                    if (!sawInputEOS)
                    {
                        //mExtractor.advance();
                    }
                }
                else
                {
                    Log.e(TAG, "inputBufIndex " + mInputBufIndex);
                }
            }

            int res = mMediaCodec.dequeueOutputBuffer(info, kTimeOutUs);

            if (res >= 0)
            {
                if (info.size > 0)
                {
                    noOutputCounter = 0;
                }

                int outputBufIndex = res;
                ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                final byte[] chunk = new byte[info.size];
                buf.get(chunk);
                buf.clear();
                if (chunk.length > 0)
                {
                    //mVideoTrack.write(chunk, 0, chunk.length);
                    if (this.mState != State.Playing)
                    {
                        mVideoPlayerHandler.onVideoPlayerPlayerStart(VideoPlayer.this);
                    }
                    this.mState = State.Playing;
                }
                Log.d(TAG, " release Otput Buffer ");
                mMediaCodec.releaseOutputBuffer(outputBufIndex, true); //TODO 여기서 버퍼가 화면에 풀림
                //mVideoPlayerHandler.onVideoPlayerPause();
                isPause = true;
            }
            else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                codecOutputBuffers = mMediaCodec.getOutputBuffers();

                Log.d(TAG, "output buffers have changed.");
            }
            else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
            {
                MediaFormat oformat = mMediaCodec.getOutputFormat();

                Log.d(TAG, "output format has changed to " + oformat);
            }
            else
            {
                Log.d(TAG, "dequeueOutputBuffer returned " + res);
            }
        }

        Log.d(TAG, "stopping...");

        releaseResources(true);

        this.mState = State.Stopped;
        isForceStop = true;

        if (noOutputCounter >= noOutputCounterLimit)
        {
            mVideoPlayerHandler.onVideoPlayerError(VideoPlayer.this);
        }
        else
        {
            mVideoPlayerHandler.onVideoPlayerStop(VideoPlayer.this);
        }
    }

    public void release()
    {
        stop();
        releaseResources(false);
    }

    private void releaseResources(Boolean release)
    {
        if (mExtractor != null)
        {
            mExtractor.release();
            mExtractor = null;
        }

        if (mMediaCodec != null)
        {
            if (release)
            {
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
            }

        }
    }

    public void pause()
    {
        isPause = true;
    }

    public void stop()
    {
        isForceStop = true;
    }

    boolean isSeek = false;
    int seekTime = 0;


    public void seekTo(int progress)
    {
        isSeek = true;
        seekTime = progress;
    }

    public void pauseToPlay()
    {
        isPause = false;
    }

    private DelegateHandler mVideoPlayerHandler = new DelegateHandler();

    class DelegateHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
        }

        public void onVideoPlayerPlayerStart(VideoPlayer player)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerStart(player);
            }
        }

        public void onVideoPlayerStop(VideoPlayer player)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerStop(player);
            }
        }

        public void onVideoPlayerError(VideoPlayer player)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerError(player);
            }
        }

        public void onVideoPlayerBuffering(VideoPlayer player)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerBuffering(player);
            }
        }

        public void onVideoPlayerDuration(int totalSec)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerDuration(totalSec);
            }
        }

        public void onVideoPlayerCurrentTime(int sec)
        {
            if (mListener != null)
            {
                mListener.onVideoPlayerCurrentTime(sec);
            }
        }

        public void onVideoPlayerPause()
        {
            if(mListener != null)
            {
                mListener.onVideoPlayerPause(VideoPlayer.this);
            }
        }
    };

}
