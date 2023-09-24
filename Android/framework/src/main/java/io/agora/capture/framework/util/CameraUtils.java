package io.agora.capture.framework.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.agora.capture.video.camera.VideoCapture;

public class CameraUtils {

    public static VideoCapture.FrameRateRange getClosestFrameRateRangeExactly(
            final List<VideoCapture.FrameRateRange> frameRateRanges, int targetFrameRate) {

        List<VideoCapture.FrameRateRange> includeRanges = new ArrayList<>();
        for (VideoCapture.FrameRateRange frameRateRange : frameRateRanges) {
            if(frameRateRange.min <= targetFrameRate && frameRateRange.max >= targetFrameRate){
                includeRanges.add(frameRateRange);
            }
        }

        if(includeRanges.size() == 0){
            if(targetFrameRate < frameRateRanges.get(0).min){
                return Collections.min(frameRateRanges, (o1, o2) -> o1.min - o2.min);
            }else{
                return Collections.max(frameRateRanges, (o1, o2) -> o1.max + o1.min - o2.max - o2.min);
            }
        }

        int minIndex = 0;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < includeRanges.size(); i++) {
            VideoCapture.FrameRateRange frameRateRange = includeRanges.get(i);
            int diff = Math.abs(frameRateRange.min - targetFrameRate) + Math.abs(frameRateRange.max - targetFrameRate);
            if(diff < minDiff){
                minDiff = diff;
                minIndex = i;
            }
        }

        return includeRanges.get(minIndex);
    }


    /**
     * Finds the frame rate range matching |targetFrameRate|.
     * Tries to find a range with as low of a minimum value as
     * possible to allow the camera adjust based on the lighting conditions.
     * Assumes that all frame rate values are multiplied by 1000.
     *
     * This code is mostly copied from WebRTC:
     * CameraEnumerationAndroid.getClosestSupportedFramerateRange
     * in webrtc/api/android/java/src/org/webrtc/CameraEnumerationAndroid.java
     */
    public static VideoCapture.FrameRateRange getClosestFrameRateRangeWebrtc(
            final List<VideoCapture.FrameRateRange> frameRateRanges, int targetFrameRate) {
        return Collections.min(frameRateRanges, new Comparator<VideoCapture.FrameRateRange>() {
            // Threshold and penalty weights if the upper bound is further away than
            // |MAX_FPS_DIFF_THRESHOLD| from requested.
            private static final int MAX_FPS_DIFF_THRESHOLD = 5000;
            private static final int MAX_FPS_LOW_DIFF_WEIGHT = 1;
            private static final int MAX_FPS_HIGH_DIFF_WEIGHT = 3;

            // Threshold and penalty weights if the lower bound is bigger than |MIN_FPS_THRESHOLD|.
            private static final int MIN_FPS_THRESHOLD = 8000;
            private static final int MIN_FPS_LOW_VALUE_WEIGHT = 1;
            private static final int MIN_FPS_HIGH_VALUE_WEIGHT = 4;

            // Use one weight for small |value| less than |threshold|, and another weight above.
            private int progressivePenalty(
                    int value, int threshold, int lowWeight, int highWeight) {
                return (value < threshold)
                        ? value * lowWeight
                        : threshold * lowWeight + (value - threshold) * highWeight;
            }

            int diff(VideoCapture.FrameRateRange range) {
                final int minFpsError = progressivePenalty(range.min, MIN_FPS_THRESHOLD,
                        MIN_FPS_LOW_VALUE_WEIGHT, MIN_FPS_HIGH_VALUE_WEIGHT);
                final int maxFpsError = progressivePenalty(Math.abs(targetFrameRate - range.max),
                        MAX_FPS_DIFF_THRESHOLD, MAX_FPS_LOW_DIFF_WEIGHT, MAX_FPS_HIGH_DIFF_WEIGHT);
                return minFpsError + maxFpsError;
            }

            @Override
            public int compare(VideoCapture.FrameRateRange range1, VideoCapture.FrameRateRange range2) {
                return diff(range1) - diff(range2);
            }
        });
    }
}
