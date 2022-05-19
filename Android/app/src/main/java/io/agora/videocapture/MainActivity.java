package io.agora.videocapture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import io.agora.capture.video.camera.CameraVideoManager;
import io.agora.capture.video.camera.Constant;
import io.agora.capture.video.camera.VideoCapture;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private CameraVideoManager mCameraVideoManager;
    private TextureView mVideoSurface;
    private RelativeLayout mVideoLayout;

    private SeekBar sliderWatermarkAlpha;

    private boolean mFinished;
    private boolean mJumpNext;
    private boolean mIsMirrored = true;

    private final ActivityResultLauncher<Void> imageLauncher = registerForActivityResult(new PickImage(), resultUri -> {
        if (resultUri != null) {
            doSetWatermark(resultUri);
        } else {
            showSelectNullDialog();
        }
    });

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) initCamera();
        else Toast.makeText(MainActivity.this, "相机权限被拒绝", Toast.LENGTH_SHORT).show();
    });

    private final ActivityResultLauncher<String> readStoragePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) imageLauncher.launch(null);
        else Toast.makeText(MainActivity.this, "读取权限被拒绝", Toast.LENGTH_SHORT).show();
    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        checkCameraPermission();
    }

    private void initView() {
//        sliderWatermarkAlpha = findViewById(R.id.slider_watermark_alpha);
//        sliderWatermarkAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if (fromUser)
//                    mCameraVideoManager.setWaterMarkAlpha(progress / 100f);
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {
//
//            }
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {
//
//            }
//        });
        // 长按清除水印
        findViewById(R.id.btn_watermark).setOnLongClickListener(v -> {
            mCameraVideoManager.setWaterMark(null);
            return true;
        });
    }

    private void checkCameraPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void initCamera() {
        // Preprocessor for Face Unity can be defined here
        // Now we ignore preprocessor
        // If there is a third-party preprocessor available,
        // say, FaceUnity, the camera manager is better to
        // be initialized asynchronously because FaceUnity
        // needs to load resource files from local storage.
        // The loading may block the video rendering for a
        // little while.
        mCameraVideoManager = CameraVideoManager.create(this, null, Camera.CameraInfo.CAMERA_FACING_FRONT, true);

        mCameraVideoManager.setCameraStateListener(new VideoCapture.VideoCaptureStateListener() {
            @Override
            public void onFirstCapturedFrame(int width, int height) {
                Log.i(TAG, "onFirstCapturedFrame: " + width + "x" + height);
            }

            @Override
            public void onCameraCaptureError(int error, String message) {
                Log.i(TAG, "onCameraCaptureError: error:" + error + " " + message);
                if (mCameraVideoManager != null) {
                    // When there is a camera error, the capture should
                    // be stopped to reset the internal states.
                    mCameraVideoManager.stopCapture();
                }
            }

            @Override
            public void onCameraClosed() {

            }
        });

        // Set camera capture configuration
        mCameraVideoManager.setPictureSize(640, 480);
        mCameraVideoManager.setFrameRate(24);
        mCameraVideoManager.setFacing(Constant.CAMERA_FACING_FRONT);
        mCameraVideoManager.setLocalPreviewMirror(toMirrorMode(mIsMirrored));

        // The preview surface is actually considered as
        // an on-screen consumer under the hood.
        mVideoSurface = new TextureView(this);
        mVideoLayout = findViewById(R.id.video_layout);
        mVideoLayout.addView(mVideoSurface);
        mCameraVideoManager.setLocalPreview(mVideoSurface, "Surface1");

        // Can attach other consumers here,
        // For example, rtc consumer or rtmp module

        mCameraVideoManager.startCapture();
    }

    public void onCameraChange(View view) {
        if (mCameraVideoManager != null) {
            mCameraVideoManager.switchCamera();
        }
    }

    public void onMirrorModeChanged(View view) {
        if (mCameraVideoManager != null) {
            mIsMirrored = !mIsMirrored;
            mCameraVideoManager.setLocalPreviewMirror(toMirrorMode(mIsMirrored));
        }
    }

    public void chooseImage(View v) {
        imageLauncher.launch(null);
    }

    public void jumpNext(View v){
        mJumpNext = true;
        startActivity(new Intent(MainActivity.this, NextActivity.class));
    }

    private int toMirrorMode(boolean isMirrored) {
        return isMirrored ? Constant.MIRROR_MODE_ENABLED : Constant.MIRROR_MODE_DISABLED;
    }

    private void doSetWatermark(Uri resultUri) {
        Bitmap watermarkBitmap = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(MainActivity.this.getContentResolver(), resultUri);
                watermarkBitmap = ImageDecoder.decodeBitmap(source);
            } else {
                watermarkBitmap = MediaStore.Images.Media.getBitmap(MainActivity.this.getContentResolver(), resultUri);
            }
        } catch (SecurityException e) {
            showRequestStoragePermissionDialog();
        } catch (Exception e) {
            Toast.makeText(this, "Error happened in fetching bitmap\n"+e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        if (watermarkBitmap != null) {
            mCameraVideoManager.setWaterMark(watermarkBitmap);
        }
//        updateSeekbar(watermarkBitmap);
    }

    private void clearWatermark(){
        mCameraVideoManager.setWaterMark(null);
        // updateUI
//        updateSeekbar(null);
    }

//    private void updateSeekbar(@Nullable Bitmap watermarkBitmap){
//        sliderWatermarkAlpha.setVisibility(watermarkBitmap == null ? View.GONE : View.VISIBLE);
//        if (watermarkBitmap != null) {
//            sliderWatermarkAlpha.setProgress(100);
//        }
//    }

    private void showRequestStoragePermissionDialog() {
        new AlertDialog.Builder(this).setMessage("由于安卓设备的多样性，您的设备要求即使使用系统自带选图功能，仍需要对APP授权才能访问此图片，是否继续？")
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)).show();
    }


    private void showSelectNullDialog() {
        if (mCameraVideoManager.getWaterMark() != null)
            new AlertDialog.Builder(this).setMessage("未做任何选择，是否清除水印？\n长按【水印】按钮也可清除。")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> clearWatermark()).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mJumpNext = false;
        if (mCameraVideoManager != null) {
            mCameraVideoManager.startCapture();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!mJumpNext && !mFinished && mCameraVideoManager != null) mCameraVideoManager.stopCapture();
    }

    @Override
    public void finish() {
        super.finish();
        mFinished = true;
        CameraVideoManager.release();
    }


    public static final class PickImage extends ActivityResultContract<Void, Uri> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Void input) {
            return new Intent(Intent.ACTION_PICK).setType("image/*");
        }

        @Nullable
        @Override
        public Uri parseResult(int resultCode, @Nullable Intent intent) {
            if (intent == null || resultCode != Activity.RESULT_OK) return null;
            return intent.getData();
        }
    }
}
