package io.agora.videocapture;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import io.agora.capture.framework.gles.MatrixOperator;
import io.agora.capture.video.camera.CameraVideoManager;
import io.agora.capture.video.camera.Constant;
import io.agora.capture.video.camera.VideoCapture;
import io.agora.capture.video.camera.WatermarkConfig;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private CameraVideoManager mCameraVideoManager;
    private View mVideoSurface;
    private FrameLayout mVideoLayout;
    private FrameLayout mSmallVideoLayout;

    private boolean mFinished;
    private boolean mJumpNext;
    private boolean mIsMirrored = false;

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

    private MatrixOperator watermarkMatrixOperator;


    private final VideoCapture.VideoCaptureStateListener videoCaptureStateListener = new VideoCapture.VideoCaptureStateListener() {
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
        public void onCameraOpen() {
            runOnUiThread(() -> initCameraSettingView());
        }

        @Override
        public void onCameraClosed() {

        }
    };

    private VideoCapture.FrameRateRangeSelector frameRateRangeSelector = new VideoCapture.FrameRateRangeSelector() {
        @Override
        public VideoCapture.FrameRateRange onSelectCameraFpsRange(List<VideoCapture.FrameRateRange> supportFpsRange, VideoCapture.FrameRateRange selectedRange, int frameRateScaled) {
            // 对特定机型进行适配，以处理在有些帧率下采集画面偏暗的问题
            if(Build.MODEL.startsWith("SM-G99")){
                VideoCapture.FrameRateRange desired = new VideoCapture.FrameRateRange(7 * 1000, 30 * 1000);
                if(supportFpsRange.contains(desired)){
                    return desired;
                }
            }
            return null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        checkCameraPermission();
    }

    private void initView() {
        // 长按清除水印
        findViewById(R.id.btn_watermark).setOnLongClickListener(v -> {
            clearWatermark();
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
        mCameraVideoManager = CameraVideoManager.create(this, null, Constant.CAMERA_FACING_FRONT, true, true);

        mCameraVideoManager.enableExactFrameRange(true);

        mCameraVideoManager.setCameraStateListener(videoCaptureStateListener);
        mCameraVideoManager.setFrameRateSelector(frameRateRangeSelector);

        // Set camera capture configuration
        mCameraVideoManager.setPictureSize(640, 480);
        mCameraVideoManager.setFrameRate(24);
        mCameraVideoManager.setFacing(Constant.CAMERA_FACING_FRONT);
        mCameraVideoManager.setLocalPreviewMirror(toMirrorMode(mIsMirrored));

        // The preview surface is actually considered as
        // an on-screen consumer under the hood.
        TextureView textureView = new TextureView(this);
        mVideoSurface = new FrameLayout(this);
        ((FrameLayout)mVideoSurface).addView(textureView);
        mVideoLayout = findViewById(R.id.video_layout);
        mVideoLayout.addView(mVideoSurface);
        mCameraVideoManager.setLocalPreview(textureView, MatrixOperator.ScaleType.CenterCrop, "Surface1");

        mSmallVideoLayout = findViewById(R.id.small_video_layout);
        mSmallVideoLayout.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Object tag = v.getTag();
                if (tag == null) {
                    CountDownTimer countDownTimer = new CountDownTimer(2000 * 200, 200) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            switchVideoLayout();
                        }

                        @Override
                        public void onFinish() {

                        }
                    };
                    countDownTimer.start();
                    v.setTag(countDownTimer);
                } else {
                    CountDownTimer countDownTimer = (CountDownTimer) tag;
                    countDownTimer.cancel();
                    v.setTag(null);
                }
                return true;
            }
        });
        mSmallVideoLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchVideoLayout();
            }
        });

        // Can attach other consumers here,
        // For example, rtc consumer or rtmp module
        mCameraVideoManager.startCapture();

    }

    private void initCameraSettingView() {
        Switch torchSwitch = findViewById(R.id.switch_torch);
        boolean torchSupported = mCameraVideoManager.isTorchSupported();
        torchSwitch.setVisibility(torchSupported ? View.VISIBLE: View.INVISIBLE);
        if (torchSupported) {
            torchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mCameraVideoManager.setTorchMode(isChecked);
            });
        }

        View zoomLayout = findViewById(R.id.ll_zoom);
        SeekBar zoomSeek = findViewById(R.id.seek_zoom);
        TextView zoomValueTv = findViewById(R.id.tv_zoom_value);
        boolean zoomSupported = mCameraVideoManager.isZoomSupported();
        zoomLayout.setVisibility(zoomSupported? View.VISIBLE: View.INVISIBLE);
        if(zoomSupported){
            float maxZoom = mCameraVideoManager.getMaxZoom();
            zoomSeek.setMax(100);
            zoomValueTv.setText(1 + "");
            zoomSeek.setProgress(0);
            zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int progress = seekBar.getProgress();
                    float zoomValue = 1 + progress * 1.0f / 100 * maxZoom;
                    zoomValueTv.setText(zoomValue + "");
                    mCameraVideoManager.setZoom(zoomValue);
                }
            });
        }


        SeekBar exposureSb = findViewById(R.id.seek_exposure_compensation);
        TextView exposureTv = findViewById(R.id.tv_exposure_compensation_value);
        exposureSb.setMax(100);
        int currExposure = mCameraVideoManager.getExposureCompensation();
        int maxExposure = mCameraVideoManager.getMaxExposureCompensation();
        int minExposure = mCameraVideoManager.getMinExposureCompensation();

        exposureTv.setText(currExposure + "");
        if (maxExposure > minExposure) {
            exposureSb.setProgress((currExposure - minExposure) * 100 / (maxExposure - minExposure));
        }
        exposureSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                float exposureValue = minExposure + progress * 1.0f / 100 * (maxExposure - minExposure);
                exposureTv.setText(exposureValue + "");
                mCameraVideoManager.setExposureCompensation((int) exposureValue);
            }
        });

    }

    private void switchVideoLayout() {
        if (mVideoLayout.getChildCount() > 0) {
            mVideoLayout.removeAllViews();
            mSmallVideoLayout.addView(mVideoSurface);
        } else {
            mSmallVideoLayout.removeAllViews();
            mVideoLayout.addView(mVideoSurface);
        }
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

    public void jumpNext(View v) {
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
            Toast.makeText(this, "Error happened in fetching bitmap\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        if (watermarkBitmap != null) {
            WatermarkConfig config = new WatermarkConfig(720, 1280);
            watermarkMatrixOperator = mCameraVideoManager.setWaterMark(watermarkBitmap, config);
            watermarkBitmap.recycle();

            updateWatermarkLayout(true);
        }

    }

    private void clearWatermark() {
        mCameraVideoManager.cleanWatermark();
        watermarkMatrixOperator = null;
        // updateUI
        updateWatermarkLayout(false);
    }


    private void updateWatermarkLayout(boolean visible) {
        if(watermarkMatrixOperator == null){
            visible = false;
        }
        View layout = findViewById(R.id.watermark_layout);
        if (!visible) {
            layout.setVisibility(View.GONE);
            return;
        }
        layout.setVisibility(View.VISIBLE);

        // alpha
        SeekBar sliderWatermarkAlpha = findViewById(R.id.slider_watermark_alpha);
        TextView sliderWatermarkAlphaValue = findViewById(R.id.slider_watermark_alpha_value);
        sliderWatermarkAlpha.setProgress((int) (mCameraVideoManager.getWatermarkAlpha() * 100));
        sliderWatermarkAlphaValue.setText(mCameraVideoManager.getWatermarkAlpha() + "");
        sliderWatermarkAlpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mCameraVideoManager.setWaterMarkAlpha(progress / 100f);
                    sliderWatermarkAlphaValue.setText(progress / 100f + "");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // scale
        SeekBar sliderWatermarkScale = findViewById(R.id.slider_watermark_scale);
        TextView sliderWatermarkScaleValue = findViewById(R.id.slider_watermark_scale_value);
        sliderWatermarkScale.setProgress((int) (watermarkMatrixOperator.getScaleRadio() * 100));
        sliderWatermarkScaleValue.setText("" + watermarkMatrixOperator.getScaleRadio());
        sliderWatermarkScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    watermarkMatrixOperator.setScaleRadio(progress / 100f);
                    sliderWatermarkScaleValue.setText("" + progress / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // translate x
        SeekBar sliderWatermarkTranX = findViewById(R.id.slider_watermark_tranx);
        TextView sliderWatermarkTranXValue = findViewById(R.id.slider_watermark_tranx_value);
        sliderWatermarkTranX.setProgress((int) (50f + watermarkMatrixOperator.getTranslateX() * 50f));
        sliderWatermarkTranXValue.setText(watermarkMatrixOperator.getTranslateX() + "");
        sliderWatermarkTranX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float translate = 0f;
                    if (progress > 50) {
                        translate = (progress - 50) / 50f;
                        watermarkMatrixOperator.translateX(translate);
                    } else {
                        translate = progress / 50f - 1.f;
                        watermarkMatrixOperator.translateX(translate);
                    }
                    sliderWatermarkTranXValue.setText(translate + "");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // translate y
        SeekBar sliderWatermarkTranY = findViewById(R.id.slider_watermark_trany);
        TextView sliderWatermarkTranYValue = findViewById(R.id.slider_watermark_trany_value);
        sliderWatermarkTranY.setProgress((int) (50f + watermarkMatrixOperator.getTranslateY() * 50f));
        sliderWatermarkTranYValue.setText(watermarkMatrixOperator.getTranslateY() + "");
        sliderWatermarkTranY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float translate = 0f;
                    if (progress > 50) {
                        translate = (progress - 50) / 50f;
                        watermarkMatrixOperator.translateY(translate);
                    } else {
                        translate = progress / 50f - 1.f;
                        watermarkMatrixOperator.translateY(translate);
                    }
                    sliderWatermarkTranYValue.setText(translate + "");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // rotation
        SeekBar sliderWatermarkRotation = findViewById(R.id.slider_watermark_rotate);
        TextView sliderWatermarkRotationValue = findViewById(R.id.slider_watermark_rotate_value);
        sliderWatermarkRotation.setProgress((int) (watermarkMatrixOperator.getRotation() * (100.f / 360)));
        sliderWatermarkRotationValue.setText(watermarkMatrixOperator.getRotation() + "");
        sliderWatermarkRotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float rotation = progress * (360.f / 100) ;
                    watermarkMatrixOperator.setRotation(-rotation);
                    sliderWatermarkRotationValue.setText(-rotation + "");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // FlipH
        Switch switchFlipH = findViewById(R.id.switch_flip_h);
        switchFlipH.setChecked(watermarkMatrixOperator.isFlipH());
        switchFlipH.setOnCheckedChangeListener((buttonView, isChecked) -> {
            watermarkMatrixOperator.setFlipH(isChecked);
        });

        // FlipV
        Switch switchFlipV = findViewById(R.id.switch_flip_v);
        switchFlipV.setChecked(watermarkMatrixOperator.isFlipV());
        switchFlipV.setOnCheckedChangeListener((buttonView, isChecked) -> {
            watermarkMatrixOperator.setFlipV(isChecked);
        });

        // scaleType
        findViewById(R.id.btn_center_crop).setOnClickListener(v -> {
            watermarkMatrixOperator.setScaleType(MatrixOperator.ScaleType.CenterCrop);
        });
        findViewById(R.id.btn_fit_center).setOnClickListener(v -> {
            watermarkMatrixOperator.setScaleType(MatrixOperator.ScaleType.FitCenter);
        });
        findViewById(R.id.btn_fit_xy).setOnClickListener(v -> {
            watermarkMatrixOperator.setScaleType(MatrixOperator.ScaleType.FitXY);
        });
    }

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
        if (!mJumpNext && !mFinished && mCameraVideoManager != null)
            mCameraVideoManager.stopCapture();
    }

    @Override
    public void finish() {
        super.finish();
        //mFinished = true;
        //CameraVideoManager.release();
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
