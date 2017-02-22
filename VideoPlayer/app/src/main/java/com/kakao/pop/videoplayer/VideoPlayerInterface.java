package com.kakao.pop.videoplayer;

public interface VideoPlayerInterface {

    public void onVideoPlayerStart(VideoPlayer player);

    public void onVideoPlayerPause(VideoPlayer player);

    public void onVideoPlayerStop(VideoPlayer player);

    public void onVideoPlayerError(VideoPlayer player);

    public void onVideoPlayerBuffering(VideoPlayer player);

    public void onVideoPlayerDuration(int totalSec);

    public void onVideoPlayerCurrentTime(int sec);
}
