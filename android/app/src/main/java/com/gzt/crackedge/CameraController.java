package com.gzt.crackedge;

import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/** CameraX owner: preview, lifecycle binding and still capture. */
public final class CameraController {
    public interface CaptureCallback {
        void onCaptured(@NonNull ImageProxy image);
        void onError(@NonNull Exception error);
    }

    public interface Listener {
        void onCameraError(@NonNull Exception error);
    }

    private final AppCompatActivity activity;
    private final PreviewView previewView;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private Listener listener;

    public CameraController(AppCompatActivity activity, PreviewView previewView) {
        this.activity = activity;
        this.previewView = previewView;
    }

    public void start(Listener listener) {
        this.listener = listener;
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                provider = future.get();
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(activity.getWindowManager()
                                .getDefaultDisplay().getRotation())
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
                AppLogger.i(AppConfig.TAG, "camera started");
            } catch (Exception e) {
                AppLogger.e(AppConfig.TAG, "camera start failed", e);
                if (listener != null) listener.onCameraError(e);
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    public boolean isReady() {
        return imageCapture != null;
    }

    public void takePicture(Executor executor, CaptureCallback callback) {
        final ImageCapture capture = imageCapture;
        if (capture == null) {
            callback.onError(new IllegalStateException("camera not ready"));
            return;
        }
        capture.takePicture(executor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                callback.onCaptured(image);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                callback.onError(exception);
            }
        });
    }

    public void shutdown() {
        if (provider != null) provider.unbindAll();
        imageCapture = null;
        provider = null;
        AppLogger.i(AppConfig.TAG, "camera shutdown");
    }
}
