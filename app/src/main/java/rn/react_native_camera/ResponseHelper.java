package rn.react_native_camera;

import android.support.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableMap;

/**
 * author：doujianshun on 2018/7/6 13:37
 * email：scn@163.com
 */

public class ResponseHelper {
    private static final int STATUS_FINISH = 0; // 拍照返回
    private static final int STATUS_CLOSE = 1; // 关闭
    private static final int STATUS_NO_PERMISSION = 2; // 没有存储和相机权限
    private static final int STATUS_CAMERA_NOT_AVAILABLE = 3; // 相机不可用

    public static ResponseHelper instance;

    private ResponseHelper() {

    }

    public static ResponseHelper getInstance() {
        if (instance == null) {
            instance = new ResponseHelper();
        }
        return instance;
    }

    private WritableMap response = Arguments.createMap();

    public void cleanResponse() {
        response = Arguments.createMap();
    }

    public @NonNull
    WritableMap getResponse() {
        return response;
    }

    public void putString(@NonNull final String key,
                          @NonNull final String value) {
        response.putString(key, value);
    }

    public void putInt(@NonNull final String key,
                       final int value) {
        response.putInt(key, value);
    }

    public void putBoolean(@NonNull final String key,
                           final boolean value) {
        response.putBoolean(key, value);
    }

    public void putDouble(@NonNull final String key,
                          final double value) {
        response.putDouble(key, value);
    }

    // 关闭拍照页面回调
    public void invokeCancel(@NonNull final Callback callback) {
        cleanResponse();
        response.putInt("status", STATUS_CLOSE);
        invokeResponse(callback);
    }

    public void invokeError(@NonNull final Callback callback,
                            @NonNull final String error) {
        cleanResponse();
        response.putString("error", error);
        invokeResponse(callback);
    }

    // 没有相机和存储权限回调
    public void invokeNoPermission(@NonNull final Callback callback) {
        cleanResponse();
        response.putInt("status", STATUS_NO_PERMISSION);
        invokeResponse(callback);
    }

    // 拍照完毕回调
    public void invokeFinish(@NonNull final Callback callback, String fileURL) {
        response.putInt("status", STATUS_FINISH);
        response.putString("fileURL", fileURL);
        invokeResponse(callback);
    }

    // 相机不可用回调
    public void invokeCameraNotAvailable(@NonNull final Callback callback) {
        cleanResponse();
        response.putInt("status", STATUS_CAMERA_NOT_AVAILABLE);
        invokeResponse(callback);
    }

    public void invokeResponse(@NonNull final Callback callback) {
        if (callback == null) return;
        callback.invoke(response);
    }
}