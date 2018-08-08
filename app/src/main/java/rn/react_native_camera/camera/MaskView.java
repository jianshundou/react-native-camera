package rn.react_native_camera.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.blankj.utilcode.utils.SizeUtils;

import rn.react_native_camera.R;

public class MaskView extends LinearLayout {
    private Paint maskPaint; // 绘制四周矩形阴影区域
    private Paint rectPaint; // 绘制中间透明区域矩形边界的Paint
    private Rect maskRect;
    private Paint linePaint; // 取景框的8条线
    private int screenWidth;
    private int screenHeight;
    private int lineLength;
    private TextView tvVehicleLicense;
    private TextView tvVehicleLicenseHint;

    public MaskView(Context context) {
        this(context, null);
    }

    public MaskView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaskView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MaskView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private void initView() {
        setBackgroundColor(Color.TRANSPARENT);
        WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);
        screenWidth = outMetrics.widthPixels;
        screenHeight = outMetrics.heightPixels;
        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.BLACK);
        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setAlpha(100);

        rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rectPaint.setColor(Color.parseColor("#3D3B3A"));
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(SizeUtils.dp2px(2));
        rectPaint.setAlpha(80);

        int maskLeft = (int) (screenWidth * 0.16); //取景框左边
        int maskTop = (int) (screenHeight * 0.09); //取景框上边
        int maskHeight = (int) (screenHeight * 0.79); //取景框高度
        int maskWidth = (int) (maskHeight * 1.46); //取景框宽度
        maskRect = new Rect(maskLeft, maskTop, maskLeft + maskWidth, maskTop + maskHeight);

        linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(SizeUtils.dp2px(2));
        lineLength = SizeUtils.dp2px(35);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();
        //  上
        canvas.drawRect(0, 0, width, maskRect.top, this.maskPaint);
        //  右
        canvas.drawRect(maskRect.right, maskRect.top, width, maskRect.bottom, this.maskPaint);
        //  下
        canvas.drawRect(0, maskRect.bottom, width, height, this.maskPaint);
        //  左
        canvas.drawRect(0, maskRect.top, maskRect.left, maskRect.bottom, this.maskPaint);
        canvas.drawRect(maskRect.left, maskRect.top, maskRect.right, maskRect.bottom, this.rectPaint);

        // xy的算法是：把屏幕横着(逆时针旋转90度的屏幕)，从左到右是x轴，从上到下是y轴
        canvas.drawLine(maskRect.left, maskRect.top, maskRect.left, maskRect.top + lineLength, linePaint);
        canvas.drawLine(maskRect.left, maskRect.top, maskRect.left + lineLength, maskRect.top, linePaint);

        canvas.drawLine(maskRect.right - lineLength, maskRect.top, maskRect.right, maskRect.top, linePaint);
        canvas.drawLine(maskRect.right, maskRect.top, maskRect.right, maskRect.top + lineLength, linePaint);

        canvas.drawLine(maskRect.right, maskRect.bottom, maskRect.right, maskRect.bottom - lineLength, linePaint);
        canvas.drawLine(maskRect.right, maskRect.bottom, maskRect.right - lineLength, maskRect.bottom, linePaint);

        canvas.drawLine(maskRect.left, maskRect.bottom, maskRect.left + lineLength, maskRect.bottom, linePaint);
        canvas.drawLine(maskRect.left, maskRect.bottom, maskRect.left, maskRect.bottom - lineLength, linePaint);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!changed) return;
        tvVehicleLicense = (TextView) findViewById(R.id.tvVehicleLicense);
        MarginLayoutParams layoutParams = (MarginLayoutParams) tvVehicleLicense.getLayoutParams();
        layoutParams.topMargin = maskRect.top + SizeUtils.dp2px(22);
        layoutParams.leftMargin = maskRect.left + (maskRect.width() - tvVehicleLicense.getWidth()) / 2;
        tvVehicleLicense.setLayoutParams(layoutParams);

        tvVehicleLicenseHint = (TextView) findViewById(R.id.tvVehicleLicenseHint);
        MarginLayoutParams hintLayoutParams = (MarginLayoutParams) tvVehicleLicenseHint.getLayoutParams();
        hintLayoutParams.topMargin = maskRect.bottom + SizeUtils.dp2px(9.5f);
        hintLayoutParams.leftMargin = maskRect.left + (maskRect.width() - tvVehicleLicenseHint.getWidth()) / 2;
        tvVehicleLicenseHint.setLayoutParams(hintLayoutParams);
    }

}
