package com.example.app.scanlibrary.zxing;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.imobpay.toolboxlibrary.LogUtil;

import java.io.IOException;


public final class CameraManager {

  private static final String TAG = CameraManager.class.getSimpleName();

  private static final int MIN_FRAME_WIDTH = 240  ; // = 1/8 * 1920
  private static final int MIN_FRAME_HEIGHT = 240 ; // = 2/9 * 1080
  private static final int MAX_FRAME_WIDTH = 1440 ; // = 3/4 * 1920
  private static final int MAX_FRAME_HEIGHT = 810 ; // = 3/4 * 1080

  private static CameraManager cameraManager;

  static final int SDK_INT; // Later we can use Build.VERSION.SDK_INT
  static {
    int sdkInt;
    try {
      sdkInt = Integer.parseInt(Build.VERSION.SDK);
    } catch (NumberFormatException nfe) {
      // Just to be safe
      sdkInt = 10000;
    }
    SDK_INT = sdkInt;
  }

  private final Context context;
  private final CameraConfigurationManager configManager;
  private Camera camera;
  private Rect framingRect;
  private Rect framingRectInPreview;
  private boolean initialized;
  private boolean previewing;
  private final boolean useOneShotPreviewCallback;
  private int laserFrameTopMargin;//扫描框离屏幕上方距离

  /**
   * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
   * clear the handler so it will only receive one message.
   */
  private final PreviewCallback previewCallback;
  /** Autofocus callbacks arrive here, and are dispatched to the Handler which requested them. */
  private final AutoFocusCallback autoFocusCallback;

  /**
   * Initializes this static object with the Context of the calling Activity.
   *
   * @param context The Activity which wants to use the camera.
   */
  public static void init(Context context) {
    if (cameraManager == null) {
      cameraManager = new CameraManager(context);
    }
  }

  /**
   * Gets the CameraManager singleton instance.
   *
   * @return A reference to the CameraManager singleton.
   */
  public static CameraManager get() {
    return cameraManager;
  }

  private CameraManager(Context context) {

    this.context = context;
    this.configManager = new CameraConfigurationManager(context);

    // Camera.setOneShotPreviewCallback() has a race condition in Cupcake, so we use the older
    // Camera.setPreviewCallback() on 1.5 and earlier. For Donut and later, we need to use
    // the more efficient one shot callback, as the older one can swamp the system and cause it
    // to run out of memory. We can't use SDK_INT because it was introduced in the Donut SDK.
    //useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > Build.VERSION_CODES.CUPCAKE;
    useOneShotPreviewCallback = Integer.parseInt(Build.VERSION.SDK) > 3; // 3 = Cupcake

    previewCallback = new PreviewCallback(configManager, useOneShotPreviewCallback);
    autoFocusCallback = new AutoFocusCallback();
  }

  /**
   * Opens the camera driver and initializes the hardware parameters.
   *
   * @param holder The surface object which the camera will draw preview frames into.
   * @throws IOException Indicates the camera driver failed to open.
   */
  public void openDriver(SurfaceHolder holder) throws IOException {
    if (camera == null) {
      camera = Camera.open();
      if (camera == null) {
        throw new IOException();
      }
      camera.setPreviewDisplay(holder);

      if (!initialized) {
        initialized = true;
        configManager.initFromCameraParameters(camera);
      }
      configManager.setDesiredCameraParameters(camera);

      //FIXME
 //     SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      //�Ƿ�ʹ��ǰ��
//      if (prefs.getBoolean(PreferencesActivity.KEY_FRONT_LIGHT, false)) {
//        FlashlightManager.enableFlashlight();
//      }
      FlashlightManager.enableFlashlight();
    }
  }

  /**
   * Closes the camera driver if still in use.
   */
  public void closeDriver() {
    if (camera != null) {
      FlashlightManager.disableFlashlight();
      camera.release();
      camera = null;
    }
  }

  /**
   * Asks the camera hardware to begin drawing preview frames to the screen.
   */
  public void startPreview() {
    if (camera != null && !previewing) {
      camera.startPreview();
      previewing = true;
    }
  }

  /**
   * Tells the camera to stop drawing preview frames.
   */
  public void stopPreview() {
    if (camera != null && previewing) {
      if (!useOneShotPreviewCallback) {
        camera.setPreviewCallback(null);
      }
      camera.stopPreview();
      previewCallback.setHandler(null, 0);
      autoFocusCallback.setHandler(null, 0);
      previewing = false;
    }
  }

  /**
   * A single preview frame will be returned to the handler supplied. The data will arrive as byte[]
   * in the message.obj field, with width and height encoded as message.arg1 and message.arg2,
   * respectively.
   *
   * @param handler The handler to send the message to.
   * @param message The what field of the message to be sent.
   */
  public void requestPreviewFrame(Handler handler, int message) {
    if (camera != null && previewing) {
      previewCallback.setHandler(handler, message);
      if (useOneShotPreviewCallback) {
        camera.setOneShotPreviewCallback(previewCallback);
      } else {
        camera.setPreviewCallback(previewCallback);
      }
    }
  }

  /**
   * Asks the camera hardware to perform an autofocus.
   *
   * @param handler The Handler to notify when the autofocus completes.
   * @param message The message to deliver.
   */
  public void requestAutoFocus(Handler handler, int message) {
    if (camera != null && previewing) {
      autoFocusCallback.setHandler(handler, message);
      //Log.d(TAG, "Requesting auto-focus callback");
      camera.autoFocus(autoFocusCallback);
    }
  }

  /**
   * Calculates the framing rect which the UI should draw to show the user where to place the
   * barcode. This target helps with alignment as well as forces the user to hold the device
   * far enough away to ensure the image will be in focus.
   *
   * @return The rectangle to draw on screen in window coordinates.
   */
  public Rect getFramingRect() {
    if (framingRect == null) {
      if (camera == null) {
        return null;
      }
      Point screenResolution = configManager.getScreenResolution();
      if (screenResolution == null) {
        // Called early, before init even finished
        return null;
      }
      int height;
      int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH,
              MAX_FRAME_WIDTH);
      LogUtil.showLog("findDesiredDimensionInRange","width:"+width);
      //竖屏则为正方形
      if (context.getResources().getConfiguration().orientation == Configuration
              .ORIENTATION_PORTRAIT) {
        height = width ;
      } else {
        height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT,
                MAX_FRAME_HEIGHT);
      }
      int statusBarHeight = getStatusBarHeight();//状态栏高度
      int leftOffset = (screenResolution.x - width) / 2;
      int topOffset = (screenResolution.y - height) / 2 ;
      if (laserFrameTopMargin == 0) {
        laserFrameTopMargin = topOffset - statusBarHeight;
      }else {
        laserFrameTopMargin += statusBarHeight;
      }
      framingRect = new Rect(leftOffset, laserFrameTopMargin, leftOffset + width,
              laserFrameTopMargin + height);
      Log.d(TAG, "Calculated framing rect: " + framingRect);
    }
    return framingRect;
  }

  private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
    int dim = 3 * resolution / 4 ; // Target 5/8 of each dimension
    LogUtil.showLog("findDesiredDimensionInRange","resolution"+resolution);
    LogUtil.showLog("findDesiredDimensionInRange","dim"+dim);
    LogUtil.showLog("findDesiredDimensionInRange","hardMin"+hardMin);
    LogUtil.showLog("findDesiredDimensionInRange","hardMax"+hardMax);
    if (dim < hardMin) {
      return hardMin;
    }
    if (dim > hardMax) {
      return dim;
    }
    return dim;
  }

  /**
   * 获取状态栏高度
   *
   * @return
   */
  private int getStatusBarHeight() {
    int result = 0;
    int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen",
            "android");
    if (resourceId > 0) {
      result = context.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  /**
   * Like {@link #getFramingRect} but coordinates are in terms of the preview frame,
   * not UI / screen.
   */
  public Rect getFramingRectInPreview() {
    if (framingRectInPreview == null) {
      Rect rect = new Rect(getFramingRect());
      Point cameraResolution = configManager.getCameraResolution();
      Point screenResolution = configManager.getScreenResolution();
      //modify here
//      rect.left = rect.left * cameraResolution.x / screenResolution.x;
//      rect.right = rect.right * cameraResolution.x / screenResolution.x;
//      rect.top = rect.top * cameraResolution.y / screenResolution.y;
//      rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
      if( null != cameraResolution && null != screenResolution){
        rect.left = rect.left * cameraResolution.y / screenResolution.x;
        rect.right = rect.right * cameraResolution.y / screenResolution.x;
        rect.top = rect.top * cameraResolution.x / screenResolution.y;
        rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
        framingRectInPreview = rect;
      }
    }
    return framingRectInPreview;
  }

  /**
   * Converts the result points from still resolution coordinates to screen coordinates.
   *
   * @param points The points returned by the Reader subclass through Result.getResultPoints().
   * @return An array of Points scaled to the size of the framing rect and offset appropriately
   *         so they can be drawn in screen coordinates.
   */
  /*
  public Point[] convertResultPoints(ResultPoint[] points) {
    Rect frame = getFramingRectInPreview();
    int count = points.length;
    Point[] output = new Point[count];
    for (int x = 0; x < count; x++) {
      output[x] = new Point();
      output[x].x = frame.left + (int) (points[x].getX() + 0.5f);
      output[x].y = frame.top + (int) (points[x].getY() + 0.5f);
    }
    return output;
  }
   */

  /**
   * A factory method to build the appropriate LuminanceSource object based on the format
   * of the preview buffers, as described by Camera.Parameters.
   *
   * @param data A preview frame.
   * @param width The width of the image.
   * @param height The height of the image.
   * @return A PlanarYUVLuminanceSource instance.
   */
  public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
    Rect rect = getFramingRectInPreview();
    int previewFormat = configManager.getPreviewFormat();
    String previewFormatString = configManager.getPreviewFormatString();
    switch (previewFormat) {
      // This is the standard Android format which all devices are REQUIRED to support.
      // In theory, it's the only one we should ever care about.
      case PixelFormat.YCbCr_420_SP:
      // This format has never been seen in the wild, but is compatible as we only care
      // about the Y channel, so allow it.
      case PixelFormat.YCbCr_422_SP:
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height());
      default:
        // The Samsung Moment incorrectly uses this variant instead of the 'sp' version.
        // Fortunately, it too has all the Y data up front, so we can read it.
        if ("yuv420p".equals(previewFormatString)) {
          return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top,
            rect.width(), rect.height());
        }
    }
    throw new IllegalArgumentException("Unsupported picture format: " +
        previewFormat + '/' + previewFormatString);
  }

  public Context getContext() {
		return context;
	}

  public boolean flashControlHandler(){
    if ( null ==  camera) {
      return false;
    }
    Camera.Parameters parameters=camera.getParameters();
    if(Camera.Parameters.FLASH_MODE_OFF.equals(parameters.getFlashMode())){
      turnOn(parameters);
      return true;
    }

    else if(Camera.Parameters.FLASH_MODE_TORCH.equals(parameters.getFlashMode())){
      turnOff(parameters);

    }
    return false;
  }

  private void turnOn(Camera.Parameters parameters) {
    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
    camera.setParameters(parameters);
  }
  private void turnOff(Camera.Parameters parameters) {
    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    camera.setParameters(parameters);
  }
}
