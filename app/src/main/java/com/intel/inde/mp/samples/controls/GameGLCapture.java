package com.intel.inde.mp.samples.controls;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import com.intel.inde.mp.AudioFormat;
import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.domain.CapturePipeline;
import com.intel.inde.mp.domain.IAndroidMediaObjectFactory;
import com.intel.inde.mp.domain.ICaptureSource;
import com.intel.inde.mp.domain.IMicrophoneSource;

public class GameGLCapture extends CapturePipeline {
    private ICaptureSource videoSource;
    private IMicrophoneSource audioSource;
    private boolean frameInProgress;

    public GameGLCapture(IAndroidMediaObjectFactory factory, IProgressListener progressListener) {
        super(factory, progressListener);
    }

    protected void setMediaSource() {
        this.videoSource = this.androidMediaObjectFactory.createCaptureSource();
        this.pipeline.setMediaSource(this.videoSource);
        if(this.audioSource != null) {
            this.pipeline.setMediaSource(this.audioSource);
        }

    }

    public void setTargetAudioFormat(AudioFormat mediaFormat) {
        super.setTargetAudioFormat(mediaFormat);
        this.audioSource = this.androidMediaObjectFactory.createMicrophoneSource();
        this.audioSource.configure(mediaFormat.getAudioSampleRateInHz(), mediaFormat.getAudioChannelCount());
    }

    public void stop() {
        super.stop();
    }

    public void setSurfaceSize(int width, int height) {
        this.videoSource.setSurfaceSize(width, height);
    }

    public void beginCaptureFrame() {
        if(!this.frameInProgress) {
            this.frameInProgress = true;
            this.videoSource.beginCaptureFrame();
        }
    }

    public void endCaptureFrame() {
        if(this.frameInProgress) {
            try {
                this.videoSource.endCaptureFrame();
            } catch (Exception e) {

            }
            this.frameInProgress = false;
        }
    }
}
