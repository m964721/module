package com.example.app.scanlibrary.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.app.scanlibrary.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.example.app.scanlibrary.zxing.CameraManager;
import com.example.app.scanlibrary.zxing.CaptureActivityHandler;
import com.example.app.scanlibrary.zxing.InactivityTimer;
import com.example.app.scanlibrary.zxing.ViewfinderView;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Hashtable;
import java.util.Vector;


/**
 * 扫码界面
 */
public class CaptureActivity extends Activity implements Callback , View.OnClickListener {
    private SurfaceView surfaceView ;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private String photo_path;

    private ImageView iv_scan_light;//闪光灯
    private TextView tv_scan_light, tv_scanFromAlbum;//提示文字
    private LinearLayout all_backbutton_layout, layout_scan_light;//返回按钮，底部闪光灯按钮布局

    private static final int REQUEST_CODE = 18;
    private static final int MAX_SCALE_VALUR = 160000;
    private final int FAILBACK = 100;
    private String userBlance = "";
    private Handler qrCodeHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case FAILBACK:
                    doFinish();
                    break;
                default:
                    break;
            }
        }
    };

    private void doFinish() {

        this.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_layout);
        initView();
    }

    private void initView() {
        CameraManager.init(getApplication());
        vibrate = true;
        viewfinderView = findViewById(R.id.viewfinder_view);
        viewfinderView.setCameraManager(CameraManager.get());
        viewfinderView.setLaserGridLineResId(R.mipmap.zfb_grid_scan_line);//扫描动画图片
        viewfinderView.setLaserFrameBoundColor(0xFFffe102);//四个角的颜色
        viewfinderView.setFrameLineColor(0xFFffe102);//描边颜色
        //文字宽度不得超过扫码框的宽度,扫码框设置默认占75%的屏幕宽度（竖屏）
        String hint = "请将二维码放入框内,将自动扫描";
        int hintSize = 16;
        viewfinderView.setDrawText(hint, hintSize, 0xFFffe102, false, 60);

        iv_scan_light = findViewById(R.id.iv_scan_light);
        tv_scan_light = findViewById(R.id.tv_scan_light);
        layout_scan_light = findViewById(R.id.layout_scan_light);

        tv_scanFromAlbum = findViewById(R.id.tv_scanFromAlbum);
        all_backbutton_layout = findViewById(R.id.all_backbutton_layout);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);


    }

    @Override
    protected void onResume() {
        super.onResume();

        surfaceView = findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            /**
             * SURFACE_TYPE_PUSH_BUFFERS表明该Surface不包含原生数据，Surface用到的数据由其他对象提供�?
             * * 在Camera图像预览中就使用该类型的Surface，有Camera负责提供给预览Surface数据，这样图像预览会比较流畅�?			 */
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;
        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        all_backbutton_layout.setOnClickListener(this);
        tv_scanFromAlbum.setOnClickListener(this);
        layout_scan_light.setOnClickListener(this);
    }
    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.all_backbutton_layout) {
            finish();

        } else if (i == R.id.tv_scanFromAlbum) {
            pickupPhoto();

        } else if (i == R.id.layout_scan_light) {
            boolean ret = CameraManager.get().flashControlHandler();//获取闪光灯状态
            if (ret) {
                //关闭
                iv_scan_light.setImageResource(R.mipmap.light_open);
                tv_scan_light.setTextColor(CaptureActivity.this.getResources()
                        .getColor(R.color.login_sure));
                tv_scan_light.setText("关闭手电筒");
            } else {
                //打开
                iv_scan_light.setImageResource(R.mipmap.light_close);
                tv_scan_light.setTextColor(CaptureActivity.this.getResources()
                        .getColor(R.color.white));
                tv_scan_light.setText("打开手电筒");
            }

        } else {
        }
    }


    /**
     * 从相册中选取图片
     */
    private void pickupPhoto() {
        Intent innerIntent = new Intent(); // "android.intent.action.GET_CONTENT"
        innerIntent.setAction(Intent.ACTION_PICK);
        innerIntent.setType("image/*");
        Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
        CaptureActivity.this.startActivityForResult(wrapperIntent, REQUEST_CODE);
    }

    /**
     * 将图片根据压缩比压缩成固定宽高的Bitmap，实际解析的图片大小可能和#reqWidth、#reqHeight不一样。
     *
     * @param imgPath   图片地址
     * @param reqWidth  需要压缩到的宽度
     * @param reqHeight 需要压缩到的高度
     * @return Bitmap
     */
    public Bitmap decodeSampledBitmapFromFile(String imgPath, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imgPath, options);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imgPath, options);
    }

    /**
     * 大于阀值的做压缩处理
     *
     * @return 无
     * @throws
     * @说明：
     * @Parameters 无
     */
    public static Bitmap getSmallerBitmap(Bitmap bitmap) {
        int size = bitmap.getWidth() * bitmap.getHeight() / MAX_SCALE_VALUR;
        if (size <= 1) {
            return bitmap;
        } else {
            // 如果小于
            Matrix matrix = new Matrix();
            matrix.postScale((float) (1 / Math.sqrt(size)), (float) (1 / Math.sqrt(size)));
            Bitmap resizeBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            return resizeBitmap;
        }
    }

    /**
     * 开启一个解析线程调用解析方法
     *
     * @param path
     * @return
     */
    protected Result scanningImage(String path) {
        if (TextUtils.isEmpty(path)) {

            return null;

        }
        // DecodeHintType 和EncodeHintType
        Hashtable<DecodeHintType, Object> hints = new Hashtable<DecodeHintType, Object>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8"); // 设置二维码内容的编码
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.QR_CODE);


        Bitmap resizeBitmap = decodeSampledBitmapFromFile(path, 800, 480);
        Bitmap scanBitmap = getSmallerBitmap(resizeBitmap);

        if (null == scanBitmap) {
            Toast.makeText(this, "图片错误", Toast.LENGTH_LONG);
            return null;
        }

        int px[] = new int[scanBitmap.getWidth() * scanBitmap.getHeight()];
        scanBitmap.getPixels(px, 0, scanBitmap.getWidth(), 0, 0,
                scanBitmap.getWidth(), scanBitmap.getHeight());
        RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap.getWidth(), scanBitmap.getHeight(), px);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        QRCodeReader reader = new QRCodeReader();
        try {
            return reader.decode(bitmap1, hints);
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (ChecksumException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解决中文乱码问题
     *
     * @param str
     * @return
     */
    private String recode(String str) {
        String formart = "";
        try {
            boolean ISO = Charset.forName("ISO-8859-1").newEncoder()
                    .canEncode(str);
            if (ISO) {
                formart = new String(str.getBytes("ISO-8859-1"), "GB2312");
            } else {
                formart = str;
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return formart;
    }

    /**
     * 针对小米手机做了一个特殊处理
     *
     * @return 无
     * @throws
     * @说明：
     * @Parameters 无
     */
    public Uri geturi(android.content.Intent intent) {
        Uri uri = intent.getData();
        String type = intent.getType();
        if ("file".equals(uri.getScheme()) && (null != type && type.contains("image/"))) {
            String path = uri.getEncodedPath();
            if (path != null) {
                path = Uri.decode(path);
                ContentResolver cr = this.getContentResolver();
                StringBuffer buff = new StringBuffer();
                buff.append("(").append(MediaStore.Images.ImageColumns.DATA).append("=")
                        .append("'" + path + "'").append(")");
                Cursor cur = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.ImageColumns._ID},
                        buff.toString(), null, null);
                int index = 0;
                for (cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
                    index = cur.getColumnIndex(MediaStore.Images.ImageColumns._ID);
                    // set _id value
                    index = cur.getInt(index);
                }
                if (index == 0) {
                    // do nothing
                } else {
                    Uri uri_temp = Uri
                            .parse("content://media/external/images/media/"
                                    + index);
                    if (uri_temp != null) {
                        uri = uri_temp;
                    }
                }
            }
        }
        return uri;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_CODE:
                if (null == data) {
                    return;
                }
                Uri uri = geturi(data);
                String[] proj = {MediaStore.Images.Media.DATA};
                // 获取选中图片的路径
                Cursor cursor = getContentResolver().query(uri,
                        proj, null, null, null);
                if (null != cursor) {
                    if (cursor.moveToFirst()) {
                        int column_index = cursor
                                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                        photo_path = cursor.getString(column_index);
                        if (null == photo_path || "".equals(photo_path)) {
                            System.out.println("Result:photo_path is null");
                            return;
                        }
                    }
                    cursor.close();
                } else {
                    photo_path = uri.getPath();
                    if (null == photo_path || "".equals(photo_path)) {
                        System.out.println("Result:photo_path is null");
                        return;
                    }
                }
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        Result result = scanningImage(photo_path);
                        if (result == null) {
                            Looper.prepare();
                            Toast.makeText(getApplicationContext(), "图片格式有误", Toast.LENGTH_LONG)
                                    .show();
                            Looper.loop();
                        } else {
                            handleDecode(result, null);
                        }
                    }
                }).start();
                break;
            default:
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        CameraManager.get().flashControlHandler();//关闭闪光灯
        super.onDestroy();
    }

    /**
     * Handler scan result
     *
     * @param result
     * @param barcode
     */
    public void handleDecode(Result result, Bitmap barcode) {
        if (this.isFinishing()) {
            return;
        }
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();
        String resultString = result.getText();

        if ("".equals(resultString)) {
            Toast.makeText(this,"扫描失败!",Toast.LENGTH_SHORT);
            qrCodeHandler.sendEmptyMessageDelayed(FAILBACK, 2000);
        } else {
            String qrData = recode(result.toString());

        }
    }

    //初始化相机
    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };


}