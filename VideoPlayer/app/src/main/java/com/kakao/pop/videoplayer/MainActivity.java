package com.kakao.pop.videoplayer;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements VideoPlayerInterface, SeekBar.OnSeekBarChangeListener, View.OnClickListener, SurfaceHolder.Callback {
    private static final String TAG = MainActivity.TAG;

    private Button mPlayButton = null;
    private Button mStopButton = null;

    private SeekBar mSeekProgress = null;
    private ProgressDialog mProgressDialog = null;

    VideoPlayer mVideoPlayer = null;

    private SurfaceView mSurfaceView;
    private boolean mSurfaceHolderReady = false;


    private static final int PERMISSIONS_REQUEST_CAMERA = 1;
    private static final int PERMISSIONS_REQUEST_GALLERY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPlayButton = (Button) this.findViewById(R.id.button_play);
        mPlayButton.setOnClickListener(this);
        mStopButton = (Button) this.findViewById(R.id.button_stop);
        mStopButton.setOnClickListener(this);

        mSeekProgress = (SeekBar) findViewById(R.id.seek_progress);
        mSeekProgress.setOnSeekBarChangeListener(this);
        mSeekProgress.setMax(0);
        mSeekProgress.setProgress(0);

        mSurfaceView = (SurfaceView) findViewById(R.id.playMovie_surface);
        mSurfaceView.getHolder().addCallback(this);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // There's a short delay between the start of the activity and the initialization
        // of the SurfaceHolder that backs the SurfaceView.  We don't want to try to
        // send a video stream to the SurfaceView before it has initialized, so we disable
        // the "play" button until this callback fires.
        Log.d(TAG, "surfaceCreated");
        mSurfaceHolderReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // ignore
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignore
        Log.d(TAG, "Surface destroyed");
    }


    private boolean checkCameraPermission() {
        return checkPermissions2(new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
    }

    private boolean checkGalleryPermission() {
        return checkPermissions2(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_GALLERY);
    }

    protected boolean checkPermissions2(String[] permissions, int requestCode) {
        int len = permissions.length;
        boolean[] hasPermissions = new boolean[len];
        boolean allHavePermission = true;
        final List<String> requestPermissions = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            hasPermissions[i] = hasPermission(this, permissions[i]);
            if (!hasPermissions[i])
                requestPermissions.add(permissions[i]);
            allHavePermission &= hasPermissions[i];
        }

        if (allHavePermission)
            return true;

        ActivityCompat.requestPermissions(this,
                requestPermissions.toArray(new String[requestPermissions.size()]),
                requestCode);

        return false;
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void stop()
    {
        if (this.mVideoPlayer != null)
        {
            this.mVideoPlayer.stop();
        }
    }

    private void updatePlayer(VideoPlayer.State state)
    {
        switch (state)
        {
            case Stopped:
            {

                mPlayButton.setSelected(false);
                mPlayButton.setText("Play");

                mSeekProgress.setMax(0);
                mSeekProgress.setProgress(0);

                break;
            }
            case Prepare:
            case Buffering:
            {
                mPlayButton.setSelected(false);
                mPlayButton.setText("Play");

                break;
            }
            case Pause:
            {
                break;
            }
            case Playing:
            {

                mPlayButton.setSelected(true);
                mPlayButton.setText("Pause");
                break;
            }
        }
    }

    private void pause()
    {
        if (this.mVideoPlayer != null)
        {
            this.mVideoPlayer.pause();
        }
    }

    private void play(Surface mSurface)
    {
        releaseVideoPlayer();

        mVideoPlayer = new VideoPlayer(mSurface, this);
        mVideoPlayer.setOnAudioStreamInterface(this);
        //mVideoPlayer.setUrlString("/storage/emulated/0/DCIM/Camera/test.mp4");
       // mVideoPlayer.setUrlString("/storage/emulated/0/Pictures/POP/pop_20170222_131218.mp4"); //1
        mVideoPlayer.setUrlString("/storage/emulated/0/Pictures/POP/pop_20170222_131331.mp4"); //0

        try
        {
            mVideoPlayer.play();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private void releaseVideoPlayer()
    {
        if (mVideoPlayer != null)
        {
            mVideoPlayer.stop();
            mVideoPlayer.release();
            mVideoPlayer = null;

        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        stop();
    }

    @Override
    public void onVideoPlayerStart(VideoPlayer player)
    {
        runOnUiThread(new Runnable()
        {

            @Override
            public void run()
            {
                updatePlayer(VideoPlayer.State.Playing);
            }
        });
    }

    @Override
    public void onVideoPlayerStop(VideoPlayer player)
    {
        runOnUiThread(new Runnable()
        {

            @Override
            public void run()
            {
                updatePlayer(VideoPlayer.State.Stopped);
            }
        });
    }

    @Override
    public void onVideoPlayerError(VideoPlayer player)
    {
        runOnUiThread(new Runnable()
        {

            @Override
            public void run()
            {
                updatePlayer(VideoPlayer.State.Stopped);
            }
        });
    }

    @Override
    public void onVideoPlayerBuffering(VideoPlayer player)
    {
        runOnUiThread(new Runnable()
        {

            @Override
            public void run()
            {
                updatePlayer(VideoPlayer.State.Buffering);
            }
        });
    }

    @Override
    public void onVideoPlayerDuration(final int totalSec)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (totalSec > 0)
                {
                    int min = totalSec / 60;
                    int sec = totalSec % 60;

                    mSeekProgress.setMax(totalSec * 30);
                    Log.d(TAG, "Seek Progress Max : " + totalSec);
                }
            }

        });
    }

    @Override
    public void onVideoPlayerCurrentTime(final int sec)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (!isSeekBarTouch)
                {
                    int m = sec / 60;
                    int s = sec % 60;

                    mSeekProgress.setProgress(sec);
                }
            }
        });
    }

    @Override
    public void onVideoPlayerPause(VideoPlayer player)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mPlayButton.setText("Play");
            }
        });
    }

    private boolean isSeekBarTouch = false;
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
    {
        if (this.isSeekBarTouch) {
            //progress = seekBar.getProgress();
            int testProgress = seekBar.getProgress();
            this.mVideoPlayer.seekTo(testProgress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {
        this.isSeekBarTouch = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {
        this.isSeekBarTouch = false;

        //int progress = seekBar.getProgress();

        //this.mVideoPlayer.seekTo(progress);
    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.button_play:
            {
                if (mPlayButton.isSelected())
                {
                    if (mVideoPlayer != null && mVideoPlayer.getState() == VideoPlayer.State.Pause)
                    {
                        mVideoPlayer.pauseToPlay();
                    }
                    else
                    {
                        pause();
                    }
                }
                else
                {
                    SurfaceHolder holder = mSurfaceView.getHolder();
                    Surface mSurface = holder.getSurface();

                    play(mSurface);
                }
                break;
            }
            case R.id.button_stop:
            {
                stop();
                break;
            }
        }
    }
}
