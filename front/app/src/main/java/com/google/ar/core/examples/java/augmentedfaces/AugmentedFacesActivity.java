/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.augmentedfaces;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.AugmentedFace.RegionType;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import android.widget.Button;
import android.net.Uri;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.RecordingStatus;
import com.google.ar.core.exceptions.RecordingFailedException;
import java.text.SimpleDateFormat;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import java.io.File;
import android.content.CursorLoader;
import android.database.Cursor;
import java.util.Date;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.Intent;
import android.provider.DocumentsContract;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.exceptions.PlaybackFailedException;


/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class AugmentedFacesActivity extends AppCompatActivity implements GLSurfaceView.Renderer, Chronometer.OnChronometerTickListener {
  // Represents the app's working state.
  public enum AppState {
    Idle,
    Recording,
    Playingback // New enum value.
  }

  // Tracks app's specific state changes.
  private AppState appState = AppState.Idle;
  private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;
  private TextView distanceView;
  private TextView alertView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

  private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
  private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
  private final ObjectRenderer noseObject = new ObjectRenderer();
  private final ObjectRenderer rightEarObject = new ObjectRenderer();
  private final ObjectRenderer leftEarObject = new ObjectRenderer();
  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] noseMatrix = new float[16];
  private final float[] rightEarMatrix = new float[16];
  private final float[] leftEarMatrix = new float[16];
  private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};
  private double distance = 0.0;
  private final String MP4_VIDEO_MIME_TYPE = "video/mp4";
  private final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
  private int REQUEST_MP4_SELECTOR = 1;
  private boolean hasSetTextureNames = false;

  private Chronometer mChronometer;
  int current = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);
    distanceView = (TextView)findViewById(R.id.distancText);
    alertView = (TextView)findViewById(R.id.alertView);
    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
    mChronometer = findViewById(R.id.chronometer);

    //正数计时设置初始值（重置）
    mChronometer.setBase(0);
    //正数计时事件监听器，时间发生变化时可进行操作
    mChronometer.setOnChronometerTickListener(this);
    //设置格式(默认"MM:SS"格式)
    mChronometer.setFormat("%s");
    mChronometer.setText(FormatMiss(current));
    initData();

    // Set up renderer.
    surfaceView.setPreserveEGLContextOnPause(true);
    surfaceView.setEGLContextClientVersion(2);
    surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
    surfaceView.setRenderer(this);
    surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    surfaceView.setWillNotDraw(false);

    distanceView.setVisibility(View.INVISIBLE);
    alertView.setText((String) this.getResources().getText(R.string.too_far));
    alertView.setVisibility(View.VISIBLE);
    installRequested = false;
  }

  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session and configure it to use a front-facing (selfie) camera.
        session = new Session(/* context= */ this, EnumSet.noneOf(Session.Feature.class));
        CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
        cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
        List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
        if (!cameraConfigs.isEmpty()) {
          // Element 0 contains the camera config that best matches the session feature
          // and filter settings.
          session.setCameraConfig(cameraConfigs.get(0));
        } else {
          message = "This device does not have a front-facing (selfie) camera";
          exception = new UnavailableDeviceNotCompatibleException(message);
        }
        configureSession();

      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        Log.e(TAG, "Exception creating session", exception);
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
          .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(GL10 gl, EGLConfig config) {
    GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

    // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    try {
      // Create the texture and pass it to ARCore session to be filled during update().
      backgroundRenderer.createOnGlThread(/*context=*/ this);
      /*augmentedFaceRenderer.createOnGlThread(this, "models/freckles.png");
      augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      noseObject.createOnGlThread(*//*context=*//* this, "models/nose.obj", "models/nose_fur.png");
      noseObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      noseObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      rightEarObject.createOnGlThread(this, "models/forehead_right.obj", "models/ear_fur.png");
      rightEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      rightEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
      leftEarObject.createOnGlThread(this, "models/forehead_left.obj", "models/ear_fur.png");
      leftEarObject.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);
      leftEarObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);*/

    } catch (IOException e) {
      Log.e(TAG, "Failed to read an asset file", e);
    }
  }

  @Override
  public void onSurfaceChanged(GL10 gl, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    GLES20.glViewport(0, 0, width, height);
  }

  @Override
  public void onDrawFrame(GL10 gl) {
    // Clear screen to notify driver it should not load any pixels from previous frame.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    if (session == null) {
      return;
    }
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    try {
      if (!hasSetTextureNames) {
        session.setCameraTextureName(backgroundRenderer.getTextureId());
        hasSetTextureNames = true;
      }

      // Check the playback status and return early if playback reaches the end.
      if (appState == AppState.Playingback
              && session.getPlaybackStatus() == PlaybackStatus.FINISHED) {
        this.runOnUiThread(this::stopPlayingback);
        return;
      }

      // Obtain the current frame from ARSession. When the configuration is set to
      // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
      // camera framerate.
      Frame frame = session.update();
      Camera camera = frame.getCamera();

      // Get projection matrix.
      float[] projectionMatrix = new float[16];
      camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

      // Get camera matrix and draw.
      float[] viewMatrix = new float[16];
      camera.getViewMatrix(viewMatrix, 0);

      // Compute lighting from average intensity of the image.
      // The first three components are color scaling factors.
      // The last one is the average pixel intensity in gamma space.
      final float[] colorCorrectionRgba = new float[4];
      frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

      // If frame is ready, render camera preview image to the GL surface.
      backgroundRenderer.draw(frame);

      // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
      trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

      // ARCore's face detection works best on upright faces, relative to gravity.
      // If the device cannot determine a screen side aligned with gravity, face
      // detection may not work optimally.
      Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
      for (AugmentedFace face : faces) {
        if (face.getTrackingState() != TrackingState.TRACKING) {
          break;
        }

        float scaleFactor = 1.0f;

        // Face objects use transparency so they must be rendered back to front without depth write.
        GLES20.glDepthMask(false);

        // Each face's region poses, mesh vertices, and mesh normals are updated every frame.

        // 1. Render the face mesh first, behind any 3D objects attached to the face regions.
        float[] modelMatrix = new float[16];
        face.getCenterPose().toMatrix(modelMatrix, 0);
        augmentedFaceRenderer.draw(
            projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face);
        Pose left = face.getRegionPose(AugmentedFace.RegionType.FOREHEAD_LEFT);
        Pose right = face.getRegionPose(AugmentedFace.RegionType.FOREHEAD_RIGHT);
//              face.getm
        // AugmentedFace node
//              face.createAnchor(left);
//              face.createAnchor(right);
        float lx = left.tx();
        float ly = left.ty();
        float lz = left.tz();
        float rx = right.tx();
        float ry = right.ty();
        float rz = right.tz();
        double llength = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double rlength = Math.sqrt(rx * rx + ry * ry + rz * rz);
        BigDecimal b1 = new BigDecimal(llength);
        BigDecimal r1 = new BigDecimal(rlength);
        double spec = b1.add(r1).divide(new BigDecimal("2")).multiply(new BigDecimal("100")).floatValue();
        Log.d("wzz","-----" + llength + "----" + rlength);
        Log.d("wzz","-----" + b1.add(r1).divide(new BigDecimal("2")));
        Log.d("wzz","到屏幕距离： " + spec + "cm");
        DecimalFormat decimalFormat = new DecimalFormat("#.00");
        distanceView.setText("到屏幕距离： " + decimalFormat.format(spec) + "cm\n请将脸部置与框内，并保持距离在45厘米左右");
        if (spec < 35){
          alertView.setText((String) this.getResources().getText(R.string.too_close));
          alertView.setVisibility(View.VISIBLE);
        }else if(spec > 55){
          alertView.setText((String) this.getResources().getText(R.string.too_far));
          alertView.setVisibility(View.VISIBLE);
        }else {
          alertView.setText((String) this.getResources().getText(R.string.keep));
          alertView.setVisibility(View.VISIBLE);
        }
/*        Log.d("wzz","-----" + decimalFormat.format((b1.add(r1).divide(new BigDecimal("2")))) + "m");
        mTv.setText("到屏幕距离： " + decimalFormat.format(spec) + "cm");*/

        /*// 2. Next, render the 3D objects attached to the forehead.
        face.getRegionPose(RegionType.FOREHEAD_RIGHT).toMatrix(rightEarMatrix, 0);
        rightEarObject.updateModelMatrix(rightEarMatrix, scaleFactor);
        rightEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

        face.getRegionPose(RegionType.FOREHEAD_LEFT).toMatrix(leftEarMatrix, 0);
        leftEarObject.updateModelMatrix(leftEarMatrix, scaleFactor);
        leftEarObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);

        // 3. Render the nose last so that it is not occluded by face mesh or by 3D objects attached
        // to the forehead regions.
        face.getRegionPose(RegionType.NOSE_TIP).toMatrix(noseMatrix, 0);
        noseObject.updateModelMatrix(noseMatrix, scaleFactor);
        noseObject.draw(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);*/
      }
    } catch (Throwable t) {
      // Avoid crashing the application due to unhandled exceptions.
      Log.e(TAG, "Exception on the OpenGL thread", t);
    } finally {
      GLES20.glDepthMask(true);
    }
  }

  private void configureSession() {
    CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
    cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
    List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
    if (!cameraConfigs.isEmpty()) {
      // Element 0 contains the camera config that best matches the session feature
      // and filter settings.
      session.setCameraConfig(cameraConfigs.get(0));
    }
    Config config = new Config(session);
    config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
    session.configure(config);
  }

  // Update the "Record" button based on app's internal state.
  private void updateRecordButton() {
    View buttonView = findViewById(R.id.record_button);
    Button button = (Button)buttonView;

    switch (appState) {

      // The app is neither recording nor playing back. The "Record" button is visible.
      case Idle:
        button.setText("Record");
        button.setVisibility(View.VISIBLE);
        break;

      // While recording, the "Record" button is visible and says "Stop".
      case Recording:
        button.setText("Stop");
        button.setVisibility(View.VISIBLE);
        break;

      // During playback, the "Record" button is not visible.
      case Playingback:
        button.setVisibility(View.INVISIBLE);
        break;
    }
  }

  // Update the "Playback" button based on app's internal state.
  private void updatePlaybackButton() {
    View buttonView = findViewById(R.id.playback_button);
    Button button = (Button)buttonView;

    switch (appState) {

      // The app is neither recording nor playing back. The "Playback" button is visible.
      case Idle:
        button.setText("Playback");
        button.setVisibility(View.VISIBLE);
        break;

      // While playing back, the "Playback" button is visible and says "Stop".
      case Playingback:
        button.setText("Stop");
        button.setVisibility(View.VISIBLE);
        break;

      // During recording, the "Playback" button is not visible.
      case Recording:
        button.setVisibility(View.INVISIBLE);
        break;
    }
  }

  // Handle the "Record" button click event.
  public void onClickRecord(View view) {
    Log.d(TAG, "onClickRecord");

    // Check the app's internal state and switch to the new state if needed.
    switch (appState) {
      // If the app is not recording, begin recording.
      case Idle: {
        distanceView.setVisibility(View.VISIBLE);
        boolean hasStarted = startRecording();
        Log.d(TAG, String.format("onClickRecord start: hasStarted %b", hasStarted));

        if (hasStarted)
          appState = AppState.Recording;

        break;
      }

      // If the app is recording, stop recording.
      case Recording: {
        distanceView.setVisibility(View.INVISIBLE);
        boolean hasStopped = stopRecording();
        Log.d(TAG, String.format("onClickRecord stop: hasStopped %b", hasStopped));

        if (hasStopped)
          appState = AppState.Idle;

        break;
      }

      default:
        // Do nothing.
        break;
    }

    updateRecordButton();
    updatePlaybackButton();
  }

  // Handle the click event of the "Playback" button.
  public void onClickPlayback(View view) {
    Log.d(TAG, "onClickPlayback");

    switch (appState) {

      // If the app is not playing back, open the file picker.
      case Idle: {
        boolean hasStarted = selectFileToPlayback();
        Log.d(TAG, String.format("onClickPlayback start: selectFileToPlayback %b", hasStarted));
        break;
      }

      // If the app is playing back, stop playing back.
      case Playingback: {
        boolean hasStopped = stopPlayingback();
        Log.d(TAG, String.format("onClickPlayback stop: hasStopped %b", hasStopped));
        break;
      }

      default:
        // Recording - do nothing.
        break;
    }

    // Update the UI for the "Record" and "Playback" buttons.
    updateRecordButton();
    updatePlaybackButton();
  }

  private boolean startRecording() {
    Uri mp4FileUri = createMp4File();
    if (mp4FileUri == null)
      return false;

    Log.d(TAG, "startRecording at: " + mp4FileUri);

    pauseARCoreSession();

    // Configure the ARCore session to start recording.
    RecordingConfig recordingConfig = new RecordingConfig(session)
            .setMp4DatasetUri(mp4FileUri)
            .setAutoStopOnPause(true);

    try {
      // Prepare the session for recording, but do not start recording yet.
      session.startRecording(recordingConfig);
    } catch (RecordingFailedException e) {
      Log.e(TAG, "startRecording - Failed to prepare to start recording", e);
      return false;
    }

    boolean canResume = resumeARCoreSession();
    if (!canResume)
      return false;

    // Correctness checking: check the ARCore session's RecordingState.
    RecordingStatus recordingStatus = session.getRecordingStatus();
    Log.d(TAG, String.format("startRecording - recordingStatus %s", recordingStatus));
    mChronometer.start();
    return recordingStatus == RecordingStatus.OK;
  }

  private void pauseARCoreSession() {
    // Pause the GLSurfaceView so that it doesn't update the ARCore session.
    // Pause the ARCore session so that we can update its configuration.
    // If the GLSurfaceView is not paused,
    //   onDrawFrame() will try to update the ARCore session
    //   while it's paused, resulting in a crash.
    surfaceView.onPause();
    session.pause();
  }

  private boolean resumeARCoreSession() {
    // We must resume the ARCore session before the GLSurfaceView.
    // Otherwise, the GLSurfaceView will try to update the ARCore session.
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
      Log.e(TAG, "CameraNotAvailableException in resumeARCoreSession", e);
      return false;
    }

    surfaceView.onResume();
    return true;
  }

  private boolean stopRecording() {
    try {
      session.stopRecording();
    } catch (RecordingFailedException e) {
      Log.e(TAG, "stopRecording - Failed to stop recording", e);
      return false;
    }
    mChronometer.stop();
    mChronometer.setBase(SystemClock.elapsedRealtime());
    current = 0;
    // Correctness checking: check if the session stopped recording.
    return session.getRecordingStatus() == RecordingStatus.NONE;
  }

  private Uri createMp4File() {
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    String mp4FileName = "arcore-" + dateFormat.format(new Date()) + ".mp4";

    ContentResolver resolver = this.getContentResolver();

    Uri videoCollection = null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      videoCollection = MediaStore.Video.Media.getContentUri(
              MediaStore.VOLUME_EXTERNAL_PRIMARY);
    } else {
      videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    }

    // Create a new Media file record.
    ContentValues newMp4FileDetails = new ContentValues();
    newMp4FileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, mp4FileName);
    newMp4FileDetails.put(MediaStore.Video.Media.MIME_TYPE, MP4_VIDEO_MIME_TYPE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // The Relative_Path column is only available since API Level 29.
      newMp4FileDetails.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
    } else {
      // Use the Data column to set path for API Level <= 28.
      File mp4FileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
      String absoluteMp4FilePath = new File(mp4FileDir, mp4FileName).getAbsolutePath();
      newMp4FileDetails.put(MediaStore.Video.Media.DATA, absoluteMp4FilePath);
    }

    Uri newMp4FileUri = resolver.insert(videoCollection, newMp4FileDetails);

    // Ensure that this file exists and can be written.
    if (newMp4FileUri == null) {
      Log.e(TAG, String.format("Failed to insert Video entity in MediaStore. API Level = %d", Build.VERSION.SDK_INT));
      return null;
    }

    // This call ensures the file exist before we pass it to the ARCore API.
    if (!testFileWriteAccess(newMp4FileUri)) {
      return null;
    }

    Log.d(TAG, String.format("createMp4File = %s, API Level = %d", newMp4FileUri, Build.VERSION.SDK_INT));

    return newMp4FileUri;
  }

  // Test if the file represented by the content Uri can be open with write access.
  private boolean testFileWriteAccess(Uri contentUri) {
    try (java.io.OutputStream mp4File = this.getContentResolver().openOutputStream(contentUri)) {
      Log.d(TAG, String.format("Success in testFileWriteAccess %s", contentUri.toString()));
      return true;
    } catch (java.io.FileNotFoundException e) {
      Log.e(TAG, String.format("FileNotFoundException in testFileWriteAccess %s", contentUri.toString()), e);
    } catch (java.io.IOException e) {
      Log.e(TAG, String.format("IOException in testFileWriteAccess %s", contentUri.toString()), e);
    }

    return false;
  }

  public boolean checkAndRequestStoragePermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
              REQUEST_WRITE_EXTERNAL_STORAGE);
      return false;
    }

    return true;
  }

  private boolean selectFileToPlayback() {
    // Start file selection from Movies directory.
    // Android 10 and above requires VOLUME_EXTERNAL_PRIMARY to write to MediaStore.
    Uri videoCollection;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      videoCollection = MediaStore.Video.Media.getContentUri(
              MediaStore.VOLUME_EXTERNAL_PRIMARY);
    } else {
      videoCollection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    }

    // Create an Intent to select a file.
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

    // Add file filters such as the MIME type, the default directory and the file category.
    intent.setType(MP4_VIDEO_MIME_TYPE); // Only select *.mp4 files
    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, videoCollection); // Set default directory
    intent.addCategory(Intent.CATEGORY_OPENABLE); // Must be files that can be opened

    this.startActivityForResult(intent, REQUEST_MP4_SELECTOR);
    return true;
  }

  // Begin playback once the user has selected the file.
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // Check request status. Log an error if the selection fails.
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != Activity.RESULT_OK || requestCode != REQUEST_MP4_SELECTOR) {
      Log.e(TAG, "onActivityResult select file failed");
      return;
    }

    Uri mp4FileUri = data.getData();
    Log.d(TAG, String.format("onActivityResult result is %s", mp4FileUri));

    // Begin playback.
    startPlayingback(mp4FileUri);
  }

  private boolean startPlayingback(Uri mp4FileUri) {
    if (mp4FileUri == null)
      return false;

    Log.d(TAG, "startPlayingback at:" + mp4FileUri);

    pauseARCoreSession();

    try {
      session.setPlaybackDatasetUri(mp4FileUri);
    } catch (PlaybackFailedException e) {
      Log.e(TAG, "startPlayingback - setPlaybackDataset failed", e);
    }

    // The session's camera texture name becomes invalid when the
    // ARCore session is set to play back.
    // Workaround: Reset the Texture to start Playback
    // so it doesn't crashes with AR_ERROR_TEXTURE_NOT_SET.
    hasSetTextureNames = false;

    boolean canResume = resumeARCoreSession();
    if (!canResume)
      return false;

    PlaybackStatus playbackStatus = session.getPlaybackStatus();
    Log.d(TAG, String.format("startPlayingback - playbackStatus %s", playbackStatus));


    if (playbackStatus != PlaybackStatus.OK) { // Correctness check
      return false;
    }

    appState = AppState.Playingback;
    updateRecordButton();
    updatePlaybackButton();
    mChronometer.start();
    return true;
  }

  // Stop the current playback, and restore app status to Idle.
  private boolean stopPlayingback() {
    // Correctness check, only stop playing back when the app is playing back.
    if (appState != AppState.Playingback)
      return false;

    pauseARCoreSession();

    // Close the current session and create a new session.
    session.close();
    try {
      session = new Session(this);
    } catch (UnavailableArcoreNotInstalledException
            |UnavailableApkTooOldException
            |UnavailableSdkTooOldException
            |UnavailableDeviceNotCompatibleException e) {
      Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session", e);
      return false;
    }
    configureSession();

    boolean canResume = resumeARCoreSession();
    if (!canResume)
      return false;

    // A new session will not have a camera texture name.
    // Manually set hasSetTextureNames to false to trigger a reset.
    hasSetTextureNames = false;

    // Reset appState to Idle, and update the "Record" and "Playback" buttons.
    appState = AppState.Idle;
    updateRecordButton();
    updatePlaybackButton();
    mChronometer.stop();
    mChronometer.setBase(SystemClock.elapsedRealtime());
    current = 0;
    return true;
  }

  //正数计时
  private void initData() {
    mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
      @Override
      public void onChronometerTick(Chronometer chronometer) {
        current++;
        chronometer.setText(FormatMiss(current));
      }
    });
  }

  @Override
  public void onChronometerTick(Chronometer chronometer) {

  }

  //正数计时显示格式
  public static String FormatMiss(int time) {
    String hh = time / 3600 > 9 ? time / 3600 + "" : "0" + time / 3600;
    String mm = (time % 3600) / 60 > 9 ? (time % 3600) / 60 + "" : "0" + (time % 3600) / 60;
    String ss = (time % 3600) % 60 > 9 ? (time % 3600) % 60 + "" : "0" + (time % 3600) % 60;
    return hh + ":" + mm + ":" + ss;
  }
}
