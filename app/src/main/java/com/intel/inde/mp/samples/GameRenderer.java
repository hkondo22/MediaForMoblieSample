// Copyright (c) 2014, Intel Corporation
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
// 1. Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.intel.inde.mp.samples;

import android.content.Context;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Handler;
import com.intel.inde.mp.IProgressListener;
import com.intel.inde.mp.StreamingParameters;
import com.intel.inde.mp.android.graphics.EglUtil;
import com.intel.inde.mp.android.graphics.FrameBuffer;
import com.intel.inde.mp.android.graphics.FullFrameTexture;
import com.intel.inde.mp.android.graphics.ShaderProgram;
import com.intel.inde.mp.domain.Resolution;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GameRenderer implements GLSurfaceView.Renderer {
    final int TARGET_FPS = 30;

    public enum RenderingMethod {
        RenderTwice,
        FrameBuffer
    }

    static float TRIANGLE_COORDINATES[] = {
        0.0f, 0.622008459f, 0.0f,
        1.0f, 0.0f, 0.0f, 1.0f,

        -0.5f, -0.311004243f, 0.0f,
        0.0f, 0.0f, 1.0f, 1.0f,

        0.5f, -0.311004243f, 0.0f,
        0.0f, 1.0f, 0.0f, 1.0f
    };

    private static final String VERTEX_SHADER =
        "uniform mat4 u_MVPMatrix;\n" +
            "attribute vec4 a_Position;\n" +
            "attribute vec4 a_Color;\n" +
            "varying vec4 v_Color;\n" +
            "void main() {\n" +
            "v_Color = a_Color;\n" +
            "gl_Position = u_MVPMatrix * a_Position;}";

    private static final String FRAGMENT_SHADER =
        "precision mediump float;\n" +
            "varying vec4 v_Color;\n" +
            "void main() {\n" +
            "gl_FragColor = v_Color; }";

    private VideoCapture videoCapture;

    private FrameBuffer frameBuffer = new FrameBuffer(EglUtil.getInstance());
    private ShaderProgram shader = new ShaderProgram(EglUtil.getInstance());

    private FullFrameTexture texture;

    private FPSCounter fpsCounter;
    private Handler handler;

    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] mvpMatrix = new float[16];

    private float[] projectionMatrix = new float[16];

    private final FloatBuffer vertices;

    private int width;
    private int height;
    private Rect videoViewport;

    private long frameCount = 0;

    RenderingMethod renderingMethod;

    private AsyncTask<String, String, String> renderTask = null;
    private GLSurfaceView surfaceView;

    public GameRenderer(Context context, Handler handler, IProgressListener progressListener, GLSurfaceView surfaceView) {
        this.handler = handler;
        this.videoCapture = new VideoCapture(context, progressListener);
        this.surfaceView = surfaceView;

        fpsCounter = new FPSCounter(20);

        vertices = ByteBuffer.allocateDirect(TRIANGLE_COORDINATES.length * 4).
            order(ByteOrder.nativeOrder()).
            asFloatBuffer();

        vertices.put(TRIANGLE_COORDINATES).position(0);
    }

    public void setRenderingMethod(RenderingMethod method) {
        renderingMethod = method;
    }

    public void startCapturing(StreamingParameters params) throws IOException {
        if (videoCapture == null) {
            return;
        }
        synchronized (videoCapture) {
            videoCapture.start(params);
            startRenderTask();
        }
    }

    public void startCapturing(String videoPath) throws IOException {
        if (videoCapture == null) {
            return;
        }
        stopRenderTask();
        synchronized (videoCapture) {
            videoCapture.start(videoPath);
            startRenderTask();
        }
    }

    public void stopCapturing() {
        if (videoCapture == null) {
            return;
        }
        synchronized (videoCapture) {
            if (videoCapture.isStarted()) {
                videoCapture.stop();
            }
        }
    }

    public boolean isCapturingStarted() {
        return videoCapture.isStarted();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        shader.create(VERTEX_SHADER, FRAGMENT_SHADER);

        Matrix.setLookAtM(viewMatrix, 0, 0.0f, 0.0f, 1.5f, 0.0f, 0.0f, -2.0f, 0.0f, 1.0f, 0.0f);

        if (texture != null) {
            texture.release();
            texture = null;
        }

        texture = new FullFrameTexture();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

        GLES20.glViewport(0, 0, width, height);

        final float ratioDisplay = (float) width / height;

        Matrix.frustumM(projectionMatrix, 0, -ratioDisplay, ratioDisplay, -1.0f, 1.0f, 1.0f, 10.0f);

        this.width = width;
        this.height = height;

        frameBuffer.setResolution(new Resolution(this.width, this.height));

        videoViewport = new Rect();

        videoViewport.left = 0;
        videoViewport.top = 0;

        // Landscape
        if (ratioDisplay > 1.0f) {
            videoViewport.right = videoCapture.getFrameWidth();
            videoViewport.bottom = (int) (videoCapture.getFrameWidth() / ratioDisplay);
        } else {
            videoViewport.bottom = videoCapture.getFrameHeight();
            videoViewport.right = (int) (videoCapture.getFrameHeight() * ratioDisplay);
        }

        videoViewport.offsetTo((videoCapture.getFrameWidth() - videoViewport.right) / 2, (videoCapture.getFrameHeight() - videoViewport.bottom) / 2);
    }

    public void startRenderTask() {
        if (renderTask == null) {
            renderTask = new AsyncTask<String, String, String>() {
                @Override
                protected String doInBackground(String... params) {
                    long endTime = System.currentTimeMillis();
                    while (!isCancelled()) {

                        captureFrame();

                        long time = 1000 / TARGET_FPS - (System.currentTimeMillis() - endTime);
                        endTime = time > 0 ? System.currentTimeMillis() + time : System.currentTimeMillis();
                        try {
                            if (time > 0) {
                                Thread.sleep(time);
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                    return null;
                }
            };
            renderTask.execute();
        }
    }

    public void stopRenderTask() {
        if (renderTask != null) {
            renderTask.cancel(true);
            renderTask = null;
        }
    }

    private void captureFrame() {
        surfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                _captureFrame();
            }
        });
    }

    private void _captureFrame() {
        synchronized (videoCapture) {
            if (!videoCapture.isConfigured()) {
                return;
            }
            if (renderingMethod == RenderingMethod.FrameBuffer.RenderTwice) {
                if (videoCapture.beginCaptureFrame()) {
                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glViewport(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());

                    GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                    GLES20.glScissor(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());

                    renderScene();

                    GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

                    videoCapture.endCaptureFrame();
                }
            } else {
                if (videoCapture.beginCaptureFrame()) {
                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glViewport(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());

                    texture.draw(frameBuffer.getTextureId());

                    videoCapture.endCaptureFrame();
                }
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        update();

        GLES20.glViewport(0, 0, width, height);

        if (!videoCapture.isStarted()) {
            renderScene();
        } else {
            if (renderingMethod == RenderingMethod.RenderTwice) {
                renderScene();

                synchronized (videoCapture) {
                    if (!videoCapture.isConfigured()) {
                        videoCapture.configure();
                    }
                }

//                synchronized (videoCapture) {
//                    if (videoCapture.beginCaptureFrame()) {
//                        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
//                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//                        GLES20.glViewport(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());
//
//                        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
//                        GLES20.glScissor(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());
//
//                        renderScene();
//
//                        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
//
//                        videoCapture.endCaptureFrame();
//                    }
//                }
            } else {
                frameBuffer.bind();

                renderScene();

                frameBuffer.unbind();

                texture.draw(frameBuffer.getTextureId());

                synchronized (videoCapture) {
                    if (!videoCapture.isConfigured()) {
                        videoCapture.configure();
                    }
                }

//                synchronized (videoCapture) {
//                    if (videoCapture.beginCaptureFrame()) {
//                        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
//                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//                        GLES20.glViewport(videoViewport.left, videoViewport.top, videoViewport.width(), videoViewport.height());
//
//                        texture.draw(frameBuffer.getTextureId());
//
//                        videoCapture.endCaptureFrame();
//                    }
//                }
            }
        }

        if (fpsCounter.update()) {
            handler.sendMessage(handler.obtainMessage(GameCapturing.UPDATE_FPS, fpsCounter.fps(), 0));
        }
    }

    private void update() {
        float angleInDegrees = 360 * (frameCount % 60) / 60f;

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);

        frameCount++;
    }

    private void renderScene() {
        drawTriangle(vertices);
    }

    private void drawTriangle(final FloatBuffer triangle) {
        shader.use();

        int positionHandle = shader.getAttributeLocation("a_Position");
        int colorHandle = shader.getAttributeLocation("a_Color");
        int MVPMatrixHandle = shader.getAttributeLocation("u_MVPMatrix");

        GLES20.glClearColor(0.3f, 0.3f, 0.3f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        triangle.position(0);

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 7 * 4, triangle);
        GLES20.glEnableVertexAttribArray(positionHandle);

        triangle.position(3);

        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 7 * 4, triangle);
        GLES20.glEnableVertexAttribArray(colorHandle);

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

        GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);

        shader.unUse();
    }
}
