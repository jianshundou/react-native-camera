package rn.react_native_camera.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import rn.react_native_camera.R;


public class ActionSheet {

    public interface OnActionSheetSelected {
        void onClick(int whichButton);
    }

    private ActionSheet() {
    }

    public static Dialog showSheet(Context context,
                                   final OnActionSheetSelected actionSheetSelected,
                                   final OnCancelListener cancelListener, String content1, String content2,
                                   String cancel, final int resId1, final int resId2, boolean isTake) {
        final Dialog dlg = new Dialog(context, R.style.ActionSheet);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.actionsheet, null);
        final int cFullFillWidth = 10000;
        layout.setMinimumWidth(cFullFillWidth);
        TextView mTitle = (TextView) layout.findViewById(R.id.title);
        mTitle.setText(content1);
        TextView mContent = (TextView) layout.findViewById(R.id.content);
        mContent.setText(content2);
        if (!isTake)
            mTitle.setVisibility(View.GONE);
        TextView mCancel = (TextView) layout.findViewById(R.id.cancel);
        mCancel.setText(cancel);
        mTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                actionSheetSelected.onClick(resId1);
            }
        });
        mContent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                actionSheetSelected.onClick(resId2);
            }
        });

        mCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                cancelListener.onCancel(dlg);
            }
        });

        Window w = dlg.getWindow();
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.x = 0;
        final int cMakeBottom = -1000;
        lp.y = cMakeBottom;
        lp.gravity = Gravity.BOTTOM;
        dlg.onWindowAttributesChanged(lp);
        if (cancelListener != null)
            dlg.setOnCancelListener(cancelListener);
        dlg.setContentView(layout);
        dlg.show();

        return dlg;
    }
}
