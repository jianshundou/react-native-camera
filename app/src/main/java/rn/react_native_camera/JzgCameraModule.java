package rn.react_native_camera;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Toast;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import rn.react_native_camera.camera.VehicleLicenseCameraActivity;
import rn.react_native_camera.utils.ActionSheet;
import rn.react_native_camera.utils.CheckPermissionUtils;
import rn.react_native_camera.utils.RealPathUtil;
import rx.functions.Action0;

/**
 * author：doujianshun on 2018/7/6 11:24
 * email：scn@163.com
 */

public class JzgCameraModule extends ReactContextBaseJavaModule implements ActivityEventListener, DialogInterface.OnCancelListener, ActionSheet.OnActionSheetSelected {
    public JzgCameraModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "JZGVINCameraManage";
    }

    private static final int REQUEST_LAUNCH_IMAGE_CAPTURE    = 13001;
    private static final int REQUEST_LAUNCH_IMAGE_LIBRARY    = 13002;

    private final ReactApplicationContext reactContext;

    protected Callback callback;

    private ResponseHelper responseHelper = ResponseHelper.getInstance();

    @ReactMethod
    public void _showImagePicker(final Callback callback) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null)
        {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }
        this.callback = callback;

        ActionSheet.showSheet(currentActivity, this, this,
                "拍照", "从相册选择", "取消", R.id.take, R.id.photo, true);
    }

    public void launchCamera()
    {
        this.showVinCamera(this.callback);
    }

    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void showVinCamera(final Callback callback)
    {
        if (!isCameraAvailable())
        {
            responseHelper.invokeCameraNotAvailable(callback);
            return;
        }

        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null)
        {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }

        this.callback = callback;
        toCamera();
    }

    public void toCamera() {
        final Activity currentActivity = getCurrentActivity();
        Intent intent = new Intent(getActivity(), VehicleLicenseCameraActivity.class);
        currentActivity.startActivityForResult(intent, REQUEST_LAUNCH_IMAGE_CAPTURE);
    }

    private boolean isCameraAvailable() {
        return reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)
                || reactContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    public void launchImageLibrary()
    {
        this.showImagePicker(this.callback);
    }
    // NOTE: Currently not reentrant / doesn't support concurrent requests
    @ReactMethod
    public void showImagePicker(final Callback callback)
    {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            responseHelper.invokeError(callback, "can't find current Activity");
            return;
        }

        int requestCode;
        requestCode = REQUEST_LAUNCH_IMAGE_LIBRARY;
        Intent libraryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        if (libraryIntent.resolveActivity(reactContext.getPackageManager()) == null)
        {
            responseHelper.invokeError(callback, "Cannot launch photo library");
            return;
        }

        this.callback = callback;

        try
        {
            currentActivity.startActivityForResult(libraryIntent, requestCode);
        }
        catch (ActivityNotFoundException e)
        {
            e.printStackTrace();
            responseHelper.invokeError(callback, "Cannot launch photo library");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //robustness code
        responseHelper.cleanResponse();
        // user cancel
        if (resultCode != Activity.RESULT_OK)
        {
            responseHelper.invokeCancel(callback);
            callback = null;
            return;
        }

        Uri uri = null;
        String realPath = "";
        switch (requestCode)
        {
            case REQUEST_LAUNCH_IMAGE_CAPTURE:
                String imgPath = data.getStringExtra("imgPath");
                if (TextUtils.isEmpty(imgPath))
                    return;
                File file = new File(imgPath);
                if (!file.exists()) {
                    Toast.makeText(getContext(), "拍摄的图片不存在，请重新拍摄", Toast.LENGTH_SHORT).show();
                    return;
                }
                realPath = file.getAbsolutePath();
                break;

            case REQUEST_LAUNCH_IMAGE_LIBRARY:
                uri = data.getData();
                realPath = RealPathUtil.getRealPathFromURI(reactContext, uri);
                final boolean isUrl = !TextUtils.isEmpty(realPath) &&
                        Patterns.WEB_URL.matcher(realPath).matches();
                if (realPath == null || isUrl)
                {
                    try
                    {
                        File file1 = createFileFromURI(uri);
                        realPath = file1.getAbsolutePath();
                        uri = Uri.fromFile(file1);
                    }
                    catch (Exception e)
                    {
                        // image not in cache
                        responseHelper.putString("error", "Could not read photo");
                        responseHelper.putString("uri", uri.toString());
                        responseHelper.invokeResponse(callback);
                        callback = null;
                        return;
                    }
                }
                break;
        }
        responseHelper.invokeFinish(callback, realPath);
        callback = null;
    }

    public Context getContext()
    {
        return getReactApplicationContext();
    }

    public @NonNull Activity getActivity()
    {
        return getCurrentActivity();
    }

    /**
     * Create a file from uri to allow image picking of image in disk cache
     * (Exemple: facebook image, google image etc..)
     *
     * @doc =>
     * https://github.com/nostra13/Android-Universal-Image-Loader#load--display-task-flow
     *
     * @param uri
     * @return File
     * @throws Exception
     */
    private File createFileFromURI(Uri uri) throws Exception {
        File file = new File(reactContext.getExternalCacheDir(), "photo-" + uri.getLastPathSegment());
        InputStream input = reactContext.getContentResolver().openInputStream(uri);
        OutputStream output = new FileOutputStream(file);

        try {
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            output.close();
            input.close();
        }

        return file;
    }

    @Override
    public void onCancel(DialogInterface dialog) {

    }

    @Override
    public void onClick(final int whichButton) {
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (whichButton == R.id.photo) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        launchImageLibrary();
                    } else {
                        CheckPermissionUtils.checkPhotoPermissions(getActivity(), new Action0() {
                            @Override
                            public void call() {
                                launchImageLibrary();
                            }
                        }, callback);
                    }

                } else if (whichButton == R.id.take) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        launchCamera();
                    } else {
                        CheckPermissionUtils.checkPhotoPermissions(getActivity(), new Action0() {
                            @Override
                            public void call() {
                                launchCamera();
                            }
                        }, callback);
                    }
                }
            }
        });

    }
}
