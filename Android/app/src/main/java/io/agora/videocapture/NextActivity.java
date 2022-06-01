package io.agora.videocapture;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import io.agora.capture.video.camera.CameraVideoManager;
import io.agora.capture.video.camera.Constant;

public class NextActivity extends AppCompatActivity {
    private CameraVideoManager mCameraVideoManager;
    private FrameLayout videoContainer;
    private boolean isFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_next);
        videoContainer = findViewById(R.id.fl_video_cotainer);
        mCameraVideoManager = CameraVideoManager.getInstance();

        TextureView textureView = new TextureView(this);
        mCameraVideoManager.setPictureSize(640, 480);
        mCameraVideoManager.setFrameRate(24);
        mCameraVideoManager.setFacing(Constant.CAMERA_FACING_FRONT);
        mCameraVideoManager.setLocalPreview(textureView);
        videoContainer.removeAllViews();
        videoContainer.addView(textureView);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCameraVideoManager.updatePreviewOrientation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraVideoManager.startCapture();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(!isFinish){
            mCameraVideoManager.stopCapture();
        }
    }

    @Override
    public void finish() {
        isFinish = true;
        super.finish();
    }

}
