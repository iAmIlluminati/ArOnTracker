/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gru.common.helpers;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Session;

/**
 * Helper to manage the sample snackbar. Hides the Android boilerplate code, and exposes simpler
 * methods.
 */
public final class SnackbarHelper {
  private static final int BACKGROUND_COLOR = 0xbf323232;
  private Snackbar messageSnackbar;
  private enum DismissBehavior { HIDE, SHOW, FINISH };
  private int maxLines = 2;
  private String lastMessage = "";
  private View snackbarView;

  public boolean isShowing() {
    return messageSnackbar != null;
  }
  public String showingValue() {
    return lastMessage;
  }

  /** Shows a snackbar with a given message. */
  public void showMessage(Activity activity, String message) {
    if (!message.isEmpty() && (!isShowing() || !lastMessage.equals(message))) {
      lastMessage = message;
      show(activity, message, DismissBehavior.HIDE);
    }
  }

  /** Shows a snackbar with a given message, and a dismiss button. */
  public void showMessageWithDismiss(Activity activity, String message) {
    show(activity, message, DismissBehavior.SHOW);
  }

  /**
   * Shows a snackbar with a given error message. When dismissed, will finish the activity. Useful
   * for notifying errors, where no further interaction with the activity is possible.
   */
  public void showError(Activity activity, String errorMessage) {
    show(activity, errorMessage, DismissBehavior.FINISH);
  }

  /**
   * Hides the currently showing snackbar, if there is one. Safe to call from any thread. Safe to
   * call even if snackbar is not shown.
   */
  public void hide(Activity activity) {
    if (!isShowing()) {
      return;
    }
    lastMessage = "";
    Snackbar messageSnackbarToHide = messageSnackbar;
    messageSnackbar = null;
    activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            messageSnackbarToHide.dismiss();
          }
        });
  }

  public void setMaxLines(int lines) {
    maxLines = lines;
  }

  /**
   * Sets the view that will be used to find a suitable parent view to hold the Snackbar view.
   *
   * <p>To use the root layout ({@link android.R.id.content}), pass in {@code null}.
   *
   * @param snackbarView the view to pass to {@link
   *     com.google.android.material.snackbar.Snackbar#make(…)} which will be used to find a
   *     suitable parent, which is a {@link androidx.coordinatorlayout.widget.CoordinatorLayout}, or
   *     the window decor's content view, whichever comes first.
   */
  public void setParentView(View snackbarView) {
    this.snackbarView = snackbarView;
  }

  private void show(
      final Activity activity, final String message, final DismissBehavior dismissBehavior) {
    activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            messageSnackbar =
                Snackbar.make(
                    snackbarView == null
                        ? activity.findViewById(android.R.id.content)
                        : snackbarView,
                    message,
                    Snackbar.LENGTH_INDEFINITE);
            messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
            if (dismissBehavior != DismissBehavior.HIDE) {
              messageSnackbar.setAction(
                  "Dismiss",
                  new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                      messageSnackbar.dismiss();
                    }
                  });
              if (dismissBehavior == DismissBehavior.FINISH) {
                messageSnackbar.addCallback(
                    new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                      @Override
                      public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                        activity.finish();
                      }
                    });
              }
            }
            ((TextView)
                    messageSnackbar
                        .getView()
                        .findViewById(com.google.android.material.R.id.snackbar_text))
                .setMaxLines(maxLines);
            messageSnackbar.show();
          }
        });
  }

    /**
     * Helper to track the display rotations. In particular, the 180 degree rotations are not notified
     * by the onSurfaceChanged() callback, and thus they require listening to the android display
     * events.
     */
    public static final class DisplayRotationHelper implements DisplayManager.DisplayListener {
      private boolean viewportChanged;
      private int viewportWidth;
      private int viewportHeight;
      private final Display display;
      private final DisplayManager displayManager;
      private final CameraManager cameraManager;

      /**
       * Constructs the DisplayRotationHelper but does not register the listener yet.
       *
       * @param context the Android {@link Context}.
       */
      public DisplayRotationHelper(Context context) {
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();
      }

      /** Registers the display listener. Should be called from {@link Activity#onResume()}. */
      public void onResume() {
        displayManager.registerDisplayListener(this, null);
      }

      /** Unregisters the display listener. Should be called from {@link Activity#onPause()}. */
      public void onPause() {
        displayManager.unregisterDisplayListener(this);
      }

      /**
       * Records a change in surface dimensions. This will be later used by {@link
       * #updateSessionIfNeeded(Session)}. Should be called from {@link
       * android.opengl.GLSurfaceView.Renderer
       * #onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)}.
       *
       * @param width the updated width of the surface.
       * @param height the updated height of the surface.
       */
      public void onSurfaceChanged(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        viewportChanged = true;
      }

      /**
       * Updates the session display geometry if a change was posted either by {@link
       * #onSurfaceChanged(int, int)} call or by {@link #onDisplayChanged(int)} system callback. This
       * function should be called explicitly before each call to {@link Session#update()}. This
       * function will also clear the 'pending update' (viewportChanged) flag.
       *
       * @param session the {@link Session} object to update if display geometry changed.
       */
      public void updateSessionIfNeeded(Session session) {
        if (viewportChanged) {
          int displayRotation = display.getRotation();
          session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight);
          viewportChanged = false;
        }
      }

      /**
       *  Returns the aspect ratio of the GL surface viewport while accounting for the display rotation
       *  relative to the device camera sensor orientation.
       */
      public float getCameraSensorRelativeViewportAspectRatio(String cameraId) {
        float aspectRatio;
        int cameraSensorToDisplayRotation = getCameraSensorToDisplayRotation(cameraId);
        switch (cameraSensorToDisplayRotation) {
          case 90:
          case 270:
            aspectRatio = (float) viewportHeight / (float) viewportWidth;
            break;
          case 0:
          case 180:
            aspectRatio = (float) viewportWidth / (float) viewportHeight;
            break;
          default:
            throw new RuntimeException("Unhandled rotation: " + cameraSensorToDisplayRotation);
        }
        return aspectRatio;
      }

      /**
       * Returns the rotation of the back-facing camera with respect to the display. The value is one of
       * 0, 90, 180, 270.
       */
      public int getCameraSensorToDisplayRotation(String cameraId) {
        CameraCharacteristics characteristics;
        try {
          characteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
          throw new RuntimeException("Unable to determine display orientation", e);
        }

        // Camera sensor orientation.
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Current display orientation.
        int displayOrientation = toDegrees(display.getRotation());

        // Make sure we return 0, 90, 180, or 270 degrees.
        return (sensorOrientation - displayOrientation + 360) % 360;
      }

      private int toDegrees(int rotation) {
        switch (rotation) {
          case Surface.ROTATION_0:
            return 0;
          case Surface.ROTATION_90:
            return 90;
          case Surface.ROTATION_180:
            return 180;
          case Surface.ROTATION_270:
            return 270;
          default:
            throw new RuntimeException("Unknown rotation " + rotation);
        }
      }

      @Override
      public void onDisplayAdded(int displayId) {}

      @Override
      public void onDisplayRemoved(int displayId) {}

      @Override
      public void onDisplayChanged(int displayId) {
        viewportChanged = true;
      }
    }
}
