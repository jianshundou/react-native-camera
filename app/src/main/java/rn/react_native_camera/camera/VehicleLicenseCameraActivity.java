package rn.react_native_camera.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.blankj.utilcode.utils.ImageUtils;
import com.blankj.utilcode.utils.ScreenUtils;
import com.blankj.utilcode.utils.Utils;
import com.facebook.drawee.view.SimpleDraweeView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rn.react_native_camera.R;
import rn.react_native_camera.utils.CameraUtil;
import rn.react_native_camera.utils.FileUtils;
import rn.react_native_camera.utils.ImageCompressor;
import rn.react_native_camera.utils.LogUtil;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * 行驶证识别专用页面
 */
public class VehicleLicenseCameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {
    private static String TAG = VehicleLicenseCameraActivity.class.getName();

    private Camera mCamera;
    private SurfaceView surfaceView;
    private SurfaceHolder mHolder;
    private Context context;
    private RelativeLayout rlCaptureLayout;
    private ImageView ivCancel;
    private ImageView ivFlashToggle;//闪光灯
    private SimpleDraweeView ivBigPhoto;//拍照完成后的预览大图
    private RelativeLayout rlControl;
    private ImageView ivRecapture;//重拍
    private ImageView ivConfirm; //确认
    private ImageView ivCapture;//拍照按钮
    private FocusImageView focusImageView;

    private int mCameraId = 0;//默认前置或者后置相机 这里暂时设置为后置
    private String imgPath;//保存图片路径
    private boolean isFlashOn = false;
    private boolean showOutline = true;//是否显示轮廓图
    private boolean isSurfaceDestroyed = false;
    private ScreenListener screenListener;
    private int previewWidth;
    private int previewHeight;
    private int pictureWidth;
    private int pictureHeight;
    private int picId = 0;
    private GestureDetector gestureDetector;
    private String currentFocusMode;
    public boolean isClick = true;//true相机按钮可以点击  false不可以点击
    private int fobidRange = 0;
    private LinearLayout llBottom;
    private int screenHeight; //屏幕高度

    private String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "JZGDealerRN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //全屏显示
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        Utils.init(getApplicationContext());
        setContentView(R.layout.activity_vehicle_license_camera);
        context = this;
        gestureDetector = new GestureDetector(context, new OnDoubleClick());
        int screenHeight = ScreenUtils.getScreenHeight();
        fobidRange = (int) (screenHeight * 0.84f);
        initData();
        initView();
        initListener();
        addScreenOffListener();
    }

    private int getScreenHeight() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();// 创建了一张白纸
        windowManager.getDefaultDisplay().getMetrics(dm);// 给白纸设置宽高
        return dm.heightPixels;
    }

    private int getScreenWidth() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();// 创建了一张白纸
        windowManager.getDefaultDisplay().getMetrics(dm);// 给白纸设置宽高
        return dm.widthPixels;
    }

    private void initView() {
        focusImageView = (FocusImageView) findViewById(R.id.iv_focus);
        ivCancel = (ImageView) findViewById(R.id.ivCancel);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        ivBigPhoto = (SimpleDraweeView) findViewById(R.id.ivBigPhoto);
        ivCapture = (ImageView) findViewById(R.id.ivCapture);
        rlControl = (RelativeLayout) findViewById(R.id.rlControl);
        rlCaptureLayout = (RelativeLayout) findViewById(R.id.rlCaptureLayout);
        ivRecapture = (ImageView) findViewById(R.id.ivRecapture);
        ivConfirm = (ImageView) findViewById(R.id.ivConfirm);
        llBottom = (LinearLayout) findViewById(R.id.llBottom);
        ivFlashToggle = (ImageView) findViewById(R.id.ivFlashToggle);
        showAndHide(0);
    }

    private void addScreenOffListener() {
        screenListener = new ScreenListener(VehicleLicenseCameraActivity.this);
        screenListener.begin(new ScreenListener.ScreenStateListener() {
            @Override
            public void onScreenOn() {
                LogUtil.e(TAG, "onScreenOn");
            }

            @Override
            public void onScreenOff() {
                isSurfaceDestroyed = true;
            }

            @Override
            public void onUserPresent() {
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        screenListener.unregisterListener();
    }

    private int count = 0;
    private int firClick = 0;
    private int secClick = 0;

    private void initListener() {
        ivCancel.setOnClickListener(this);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);
        ivCapture.setOnClickListener(this);
        rlControl.setOnClickListener(this);
        ivRecapture.setOnClickListener(this);
        ivConfirm.setOnClickListener(this);
        ivFlashToggle.setOnClickListener(this);
        rlCaptureLayout.setOnClickListener(this);
        View.OnTouchListener listener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (rlControl.getVisibility() == View.VISIBLE) {//预览状态双击无视
                    return true;
                }
                gestureDetector.onTouchEvent(event);
                if (MotionEvent.ACTION_DOWN == event.getAction()) {
                    count++;
                    if (count == 1) {
                        firClick = (int) System.currentTimeMillis();
                    } else if (count == 2) {
                        secClick = (int) System.currentTimeMillis();
                        if (secClick - firClick < 1000) {//双击
                            showOutline = !showOutline;
                        }
                        count = 0;
                        firClick = 0;
                        secClick = 0;
                    }
                }
                return true;
            }
        };
        surfaceView.setOnTouchListener(listener);
    }

    private void initData() {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);
        screenHeight = outMetrics.heightPixels;
    }

    public void setPoint(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int width = focusImageView.getWidth();
        int height = focusImageView.getHeight();
        if (x < width / 2) {
            x = width / 2;
        }
        if (x > llBottom.getLeft() - width / 2) {
            x = llBottom.getLeft() - width / 2;
        }
        if (y < height / 2) {
            y = height / 2;
        }
        if (y > screenHeight - height / 2) {
            y = screenHeight - height / 2;
        }
        Point point = new Point((int) x, (int) y);

        focusImageView.startFocus(point);
        onFocus(x, y, new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (!TextUtils.isEmpty(currentFocusMode)) {
                    mCamera.getParameters().setFocusMode(currentFocusMode);
                }
                if (success) {
                    focusImageView.onFocusSuccess();
                } else {
                    //聚焦失败显示的图片，由于未找到合适的资源，这里仍显示同一张图片
                    focusImageView.onFocusFailed();
                    try {
                        mCamera.autoFocus(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * 手动聚焦
     */
    protected void onFocus(float x, float y, Camera.AutoFocusCallback callback) {
        if (y >= fobidRange)
            return;
        if (mCamera == null) {
            initCamera(mHolder);
        }
        Camera.Parameters parameters = mCamera.getParameters();
        if (parameters == null) {
            return;
        }
        currentFocusMode = parameters.getFocusMode();
        mCamera.cancelAutoFocus();
        Rect rect = calculateFocusArea(x, y);
        List<Camera.Area> meteringAreas = new ArrayList<>();
        meteringAreas.add(new Camera.Area(rect, 800));
        if (parameters.getMaxNumMeteringAreas() > 0) {
            if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                return; //cannot autoFocus
            }
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            parameters.setFocusAreas(meteringAreas);
            parameters.setMeteringAreas(meteringAreas);
            try {
                mCamera.setParameters(parameters);
                mCamera.autoFocus(callback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            try {
                mCamera.autoFocus(callback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Rect calculateFocusArea(float x, float y) {
        float focusAreaSize = 300;
        int areaSize = Float.valueOf(focusAreaSize * 1.0f).intValue();
        int centerX = (int) (x / ScreenUtils.getScreenWidth() * 2000 - 1000);
        int centerY = (int) (y / ScreenUtils.getScreenHeight() * 2000 - 1000);
        int left = clamp(centerX - areaSize / 2, -1000, 1000);
        int top = clamp(centerY - areaSize / 2, -1000, 1000);
        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right),
                Math.round(rectF.bottom));
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    @Override
    public void onBackPressed() {
        if (rlControl.getVisibility() == View.GONE) {
            onCancel();
        } else {
            if (!TextUtils.isEmpty(imgPath)) {
                new File(imgPath).delete();
            }
            if (mHolder != null) {
                try {
                    checkCamera(mCameraId);
                    startPreview(mHolder);
                    showAndHide(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void reCapture() {
        if (!TextUtils.isEmpty(imgPath)) {
            FileUtils.deleteFile(imgPath);
        }
        if (mHolder != null) {
            try {
                showAndHide(0);
                initCamera(mHolder);
                isClick = true;
            } catch (Exception e) {
                e.printStackTrace();
                isClick = true;
            }
        }
    }

    private void setFlash() {
        if (isFlashOn) {
            CameraUtil.getInstance().turnLightOn(mCamera);
        } else {
            CameraUtil.getInstance().turnLightOff(mCamera);
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.ivRecapture) {
            reCapture();

        } else if (i == R.id.ivCapture) {
            setFlash();
            if (isClick) {
                capture();
                isClick = false;
            }

        } else if (i == R.id.ivConfirm) {
            File file = new File(imgPath);
            if (file.exists() && file.canRead() && file.length() > 0) {
                releaseCamera();
                if (file.length() / 1024 > 1024) {//如果onPictureTaken处理过的照片仍然大于1M，则再次进行压缩
                    process(true);
                } else {
                    process(false);
                }
            } else {
                Toast.makeText(getApplicationContext(), "照片错误", Toast.LENGTH_SHORT).show();
            }

        } else if (i == R.id.ivCancel) {
            onCancel();
        } else if (i == R.id.ivFlashToggle) {
            isFlashOn = !isFlashOn;
            ivFlashToggle.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            setFlash();
        }
    }

    private void onCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * 获取Camera实例
     */
    private void checkCamera(int id) {
        try {
            if (mCamera == null)
                mCamera = Camera.open(id);
        } catch (Exception e) {
            e.printStackTrace();
            showPermissionRefused();
        }
    }

    private void showPermissionRefused() {
        new AlertDialog.Builder(this).setTitle("权限被拒").
                setMessage("拍照需要使用相机权限，请手动开启后再操作")
                .setCancelable(false)
                .setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                }).show();
    }

    /**
     * 预览相机
     */
    private void startPreview(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            //亲测的一个方法 基本覆盖所有手机 将预览矫正
            CameraUtil.getInstance().setCameraDisplayOrientation(this, mCameraId, mCamera);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            showPermissionRefused();
        }
    }

    private void showAndHide(int status) {
        switch (status) {
            case 0://默认
                rlControl.setVisibility(View.GONE);
                ivBigPhoto.setVisibility(View.GONE);
                ivBigPhoto.setImageURI(Uri.parse(""));
                rlCaptureLayout.setVisibility(View.VISIBLE);
                ivCapture.setVisibility(View.VISIBLE);
                ivCancel.setVisibility(View.VISIBLE);
                break;
            case 1://拍照完待确认
                rlControl.setVisibility(View.VISIBLE);
                ivBigPhoto.setVisibility(View.VISIBLE);
                ivCapture.setVisibility(View.GONE);
                ivCancel.setVisibility(View.GONE);
                rlCaptureLayout.setVisibility(View.GONE);
                break;
        }
    }

    private Bitmap scaleBitmap(Bitmap saveBitmap) {
        if (saveBitmap.getWidth() < saveBitmap.getHeight())
            saveBitmap = Bitmap.createScaledBitmap(saveBitmap, pictureHeight, pictureWidth, true);
        else
            saveBitmap = Bitmap.createScaledBitmap(saveBitmap, pictureWidth, pictureHeight, true);
        return saveBitmap;
    }

    private void adjustWidthAndHeight(int value) {
        if (pictureWidth >= pictureHeight) {
            if (pictureWidth > value) {
                pictureHeight = value * pictureHeight / pictureWidth;
                pictureWidth = value;
            }
        } else {
            if (pictureHeight > value) {
                pictureWidth = value * pictureWidth / pictureHeight;
                pictureHeight = value;
            }
        }
    }

    private Camera.Parameters parameters;

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        previewWidth = 0;
        previewHeight = 0;
        pictureWidth = 0;
        pictureHeight = 0;
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
                finish();
            }
            mCamera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated");
        isSurfaceDestroyed = false;
        checkCamera(mCameraId);

    }

    @Override
    public void surfaceChanged(final SurfaceHolder holder, int format, int width, int height) {
        LogUtil.e(TAG, "surfaceChanged");
        initCamera(holder);//实现相机的参数初始化
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtil.e(TAG, "onResume");
        if (isSurfaceDestroyed) {
            checkCamera(mCameraId);
            initCamera(mHolder);
        }
        if (mCamera == null) {
            initCamera(mHolder);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();
    }

    //相机参数的初始化设置
    private void initCamera(SurfaceHolder holder) {
        if (mCamera == null)
            checkCamera(mCameraId);
        if (mCamera == null) {
            showPermissionRefused();
            return;
        }
        try {
            parameters = mCamera.getParameters();
        } catch (Exception e) {
            showPermissionRefused();
            return;
        }

        initPictureSize();
        initPreviewSize();
        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.set("jpeg-quality", 100);
        LogUtil.e(TAG, "mCameraId=" + mCameraId);
        if (mCameraId == 0) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        setDispaly(parameters, mCamera);
        try {
            mCamera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        startPreview(holder);
        setFlash();
    }

    /***
     * 初始化预览图分辨率，遍历Camera可选的预览分辨率，按照从大到小的顺序将宽高相乘，取第一个>100W像素的宽高，设置给PreviewSize
     */
    private void initPreviewSize() {
        List<Camera.Size> sizeList = parameters.getSupportedPreviewSizes();
        if (sizeList.size() > 0) {
            if (sizeList.get(0).width < sizeList.get(sizeList.size() - 1).width) {//从小到大，则反序
                Collections.reverse(sizeList);
            }
            for (Camera.Size size : sizeList) {
                LogUtil.e(TAG, "PreviewSize-->" + size.width + "x" + size.height);
                if (size.width / 1000 == 1 && getPreScale(size)) {
                    previewWidth = size.width;
                    previewHeight = size.height;
                    break;
                }
            }
            if (previewWidth == 0 || previewHeight == 0) {//如果没有符合条件的，则取可选宽高最大值
                previewWidth = sizeList.get(0).width;
                previewHeight = sizeList.get(0).height;
            }
        } else {
            previewWidth = ScreenUtils.getScreenWidth();
            previewHeight = ScreenUtils.getScreenHeight();
            LogUtil.e(TAG, "预览图片大小取屏幕分辨率");
        }
        parameters.setPreviewSize(previewWidth, previewHeight);
        LogUtil.e(TAG, "final PreviewSize-->" + previewWidth + "*" + previewHeight);
    }

    /**
     * 华为meta7，前置摄像头，预览分辨不能正确选取16：9的分辨率
     *
     * @param size
     * @return
     */
    protected boolean getPreScale(Camera.Size size) {
        String carrier = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (("HUAWEI".equalsIgnoreCase(carrier) && "HUAWEI MT7-TL00".equalsIgnoreCase(model)) || ("Meizu".equalsIgnoreCase(carrier) && "MX6".equalsIgnoreCase(model))) {
            float w = size.width;
            float h = size.height;
            return (w / h) == (16.0f / 9.0f);
        }
        return true;
    }

    /***
     * 初始化输出图偏分辨率，遍历Camera可选的输出分辨率，按照从大到小的顺序将宽高相乘，取第一个>100W像素的宽高，设置给PictureSize
     */
    private void initPictureSize() {
        List<Camera.Size> sizeList = parameters.getSupportedPictureSizes();
        if (sizeList.size() > 0) {
            if (sizeList.get(0).width < sizeList.get(sizeList.size() - 1).width)//如果是从小到大，则反序
                Collections.reverse(sizeList);
            for (Camera.Size size : sizeList) {
                if (size.width / 1000 == 1) {//拿到第一个大于100W像素的尺寸
                    pictureWidth = size.width;
                    pictureHeight = size.height;
                    break;
                }
            }
            if (pictureWidth == 0 || pictureHeight == 0) {
                pictureWidth = sizeList.get(0).width;
                pictureHeight = sizeList.get(0).height;
            }
        } else {
            pictureWidth = ScreenUtils.getScreenWidth();
            pictureHeight = ScreenUtils.getScreenHeight();
            LogUtil.e(TAG, "输出图片大小取屏幕分辨率");
        }
        parameters.setPictureSize(pictureWidth, pictureHeight);
        LogUtil.e(TAG, "final PictureSize++>" + pictureWidth + "*" + pictureHeight);
    }

    //控制图像的正确显示方向
    private void setDispaly(Camera.Parameters parameters, Camera camera) {
        if (Integer.parseInt(Build.VERSION.SDK) >= 8) {
            setDisplayOrientation(camera, 90);
        } else {
            parameters.setRotation(90);
        }

    }

    //实现的图像的正确显示
    private void setDisplayOrientation(Camera camera, int i) {
        Method downPolymorphic;
        try {
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation", new Class[]{int.class});
            if (downPolymorphic != null) {
                downPolymorphic.invoke(camera, new Object[]{i});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.e(TAG, "surfaceDestroyed");
    }

    private void capture() {
        if (null == mCamera)
            return;
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Bitmap saveBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                LogUtil.e(TAG, "srcPhoto:" + (data.length / 1024) + "kb," + saveBitmap.getWidth() + "x" + saveBitmap.getHeight());
                imgPath = getExternalFilesDir(Environment.DIRECTORY_DCIM).getPath() + File.separator + System.currentTimeMillis() + ".jpg";
                FileUtils.saveFile(data, imgPath);//先保存到文件
                adjustWidthAndHeight(1920);//计算合适的宽高
                saveBitmap = scaleBitmap(saveBitmap);//根据上一步算出来的宽高对照片进行调整
                LogUtil.e(TAG, "ScaledPhoto:" + (saveBitmap.getByteCount() / 1024) + "kb," + saveBitmap.getWidth() + "x" + saveBitmap.getHeight());
                ImageUtils.save(saveBitmap, imgPath, Bitmap.CompressFormat.JPEG);//重新保存
                ivBigPhoto.setImageURI(Uri.parse("file://" + imgPath));
                showAndHide(1);
            }
        });
    }

    private void process(final boolean needProcess) {
//        showLoading();
        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.onNext(processPhoto(needProcess));
                subscriber.onCompleted();
            }
        }).observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
//                        dismissLoading();
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
//                        dismissLoading();
                    }

                    @Override
                    public void onNext(String path) {
                        if (TextUtils.isEmpty(path)) {
                            Toast.makeText(getApplicationContext(), "照片保存失败", Toast.LENGTH_SHORT).show();
                        } else {
                            Intent intentResult = getIntent();
                            intentResult.putExtra("imgPath", path);
                            intentResult.putExtra("picId", picId);
                            setResult(RESULT_OK, intentResult);
                            finish();
                        }
                    }
                });
    }

    /***
     * 对相册选取的照片进行压缩，缩放等处理
     * @return
     */
    private String processPhoto(boolean needProcess) {
        String dirPath = ROOT_PATH + File.separator + "vehicleLicense" + File.separator;
        FileUtils.createOrExistsDir(dirPath);
        String newPicPath = dirPath + FileUtils.getFileName(imgPath);
        boolean saved;
        if (needProcess) {
            Bitmap processedBitmap = ImageCompressor.loadBitmapFile(imgPath);
            saved = ImageCompressor.save(processedBitmap, newPicPath, Bitmap.CompressFormat.JPEG, 80);
        } else {
            saved = com.blankj.utilcode.utils.FileUtils.copyFile(imgPath, newPicPath);
        }
        boolean deleted = com.blankj.utilcode.utils.FileUtils.deleteFile(imgPath);
        return saved && deleted ? newPicPath : "";
    }

    public class OnDoubleClick extends GestureDetector.SimpleOnGestureListener {
        /*
        * 发生确定的单击时执行
        * @param e
        * @return
                */
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {//单击事件
            setPoint(e);
            return true;
        }

        /**
         * 双击发生时的通知
         *
         * @param e
         * @return
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {//双击事件
            return true;
        }

        /**
         * 双击手势过程中发生的事件，包括按下、移动和抬起事件
         *
         * @param e
         * @return
         */
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return super.onDoubleTapEvent(e);
        }

    }
}
