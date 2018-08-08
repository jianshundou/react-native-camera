package rn.react_native_camera.utils;

import android.Manifest;
import android.app.Activity;

import com.tbruyelle.rxpermissions.RxPermissions;

import com.facebook.react.bridge.Callback;

import rn.react_native_camera.ResponseHelper;
import rx.functions.Action0;
import rx.functions.Action1;

public class CheckPermissionUtils {

    /**
     * 拍照权限申请 需要CAMERA 和WRITE_EXTERNAL_STORAGE权限
     *
     * @param action 申请成功后执行的动作
     */
    public static void checkPhotoPermissions(final Activity activity, final Action0 action, final Callback callback) {
        new RxPermissions(activity)
                .request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean granted) {
                        if (granted) {
                            action.call();
                        } else {
                            ResponseHelper.getInstance().invokeNoPermission(callback);
                        }
                    }
                });
    }
}
