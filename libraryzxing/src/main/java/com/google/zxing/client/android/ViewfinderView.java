/*
 * Copyright (C) 2008 ZXing authors
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

package com.google.zxing.client.android;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.model.MNScanConfig;
import com.google.zxing.client.android.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private Context context;
    private CameraManager cameraManager;
    private final Paint paint;
    private Paint paintResultPoint;
    private Paint paintText;
    private Paint paintLine;
    private Paint paintLaser;
    private int maskColor;
    private int laserColor;

    private Result resultPoint;
    private float scaleFactor;
    private boolean showResultPoint;
    private int pointColor;
    private int pointBorderColor;
    private float resultPointRadiusCircle = 60;
    private float resultPointRadiusCorners = 60;
    private float resultPointStrokeWidth = 10;

    private Rect frame;
    private String hintMsg;
    private String hintTextColor = "#FFFFFF";
    private int hintTextSize = 14;
    private int linePosition = 0;
    private int margin;
    private int laserLineW;
    private int cornerLineH;
    private int cornerLineW;
    private int gridColumn;
    private int gridHeight;

    //扫描线风格：0线，1网格
    private MNScanConfig.LaserStyle laserStyle = MNScanConfig.LaserStyle.Line;

    private MNScanConfig mnScanConfig;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintResultPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLaser = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.mn_scan_viewfinder_mask);
        laserColor = resources.getColor(R.color.mn_scan_viewfinder_laser);
        pointColor = resources.getColor(R.color.mn_scan_viewfinder_laser_result_point);
        pointBorderColor = resources.getColor(R.color.mn_scan_viewfinder_laser_result_point_border);
        hintMsg = "将二维码放入框内，即可自动扫描";
        //文字
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(CommonUtils.sp2px(context, hintTextSize));
        paintText.setTextAlign(Paint.Align.CENTER);
        //四角
        paintLine.setColor(laserColor);
        //扫描线
        paintLaser.setColor(laserColor);
        paintResultPoint.setColor(laserColor);
        //初始化数据大小
        initSize();
    }

    private void initSize() {
        //间距
        margin = CommonUtils.dip2px(context, 4);
        //扫描线的宽度
        laserLineW = CommonUtils.dip2px(context, 3);
        //四角线块
        cornerLineH = CommonUtils.dip2px(context, 2);
        cornerLineW = CommonUtils.dip2px(context, 14);
        //网格扫描线先关配置
        gridColumn = 24;
        gridHeight = 0;
    }

    /**
     * 设置颜色
     *
     * @param laserColor
     */
    public void setLaserColor(int laserColor) {
        this.laserColor = laserColor;
        paintLine.setColor(this.laserColor);
        paintLaser.setColor(this.laserColor);
    }

    /**
     * 扫描线的样式
     *
     * @param laserStyle
     */
    public void setLaserStyle(MNScanConfig.LaserStyle laserStyle) {
        this.laserStyle = laserStyle;
    }

    /**
     * 背景色
     *
     * @param maskColor
     */
    public void setMaskColor(int maskColor) {
        this.maskColor = maskColor;
    }

    /**
     * 网格扫描列数
     *
     * @param gridColumn
     */
    public void setGridScannerColumn(int gridColumn) {
        if (gridColumn > 0) {
            this.gridColumn = gridColumn;
        }
    }

    /**
     * 网格扫描高度，默认扫描框的高度
     *
     * @param gridHeight
     */
    public void setGridScannerHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }


    public void setScanConfig(MNScanConfig scanConfig) {
        this.mnScanConfig = scanConfig;

        initResultPointConfigs();

        //扫描文字配置
        setHintText(mnScanConfig.getScanHintText(), mnScanConfig.getScanHintTextColor(), mnScanConfig.getScanHintTextSize());

        //扫描线相关配置
        if (!TextUtils.isEmpty(mnScanConfig.getScanColor())) {
            setLaserColor(Color.parseColor(mnScanConfig.getScanColor()));
        }
        setLaserStyle(mnScanConfig.getLaserStyle());

        if (!TextUtils.isEmpty(mnScanConfig.getBgColor())) {
            setMaskColor(Color.parseColor(mnScanConfig.getBgColor()));
        }
        setGridScannerColumn(mnScanConfig.getGridScanLineColumn());
        setGridScannerHeight(mnScanConfig.getGridScanLineHeight());
    }

    private void initResultPointConfigs() {
        showResultPoint = mnScanConfig.isShowResultPoint();
        resultPointRadiusCorners = mnScanConfig.getResultPointCorners();
        resultPointRadiusCircle = mnScanConfig.getResultPointRadiusCircle();
        resultPointStrokeWidth = mnScanConfig.getResultPointStrokeWidth();
        String resultPointColor = mnScanConfig.getResultPointColor();
        String resultPointStrokeColor = mnScanConfig.getResultPointStrokeColor();
        if(!TextUtils.isEmpty(resultPointColor)){
            pointColor = Color.parseColor(resultPointColor);
        }
        if(!TextUtils.isEmpty(resultPointStrokeColor)){
            pointBorderColor = Color.parseColor(resultPointStrokeColor);
        }
    }

    /**
     * 设置文案
     */
    public void setHintText(String hintMsg, String hintTextColor, int hintTextSize) {
        //文字
        if (!TextUtils.isEmpty(hintMsg)) {
            this.hintMsg = hintMsg;
        } else {
            this.hintMsg = "";
        }
        //文字颜色
        if (!TextUtils.isEmpty(hintTextColor)) {
            this.hintTextColor = hintTextColor;
        }
        //文字大小
        if (hintTextSize > 0) {
            this.hintTextSize = hintTextSize;
        }
        paintText.setColor(Color.parseColor(this.hintTextColor));
        paintText.setTextSize(CommonUtils.sp2px(context, this.hintTextSize));
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }


    @SuppressLint("DrawAllocation")
    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            return; // not ready yet, early draw before done configuring
        }
        frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // 半透明背景
        paint.setColor(maskColor);

        paintLine.setShader(null);
        //四角线块
        int rectH = cornerLineW;
        int rectW = cornerLineH;
        //判断是不是全屏模式
        if (mnScanConfig != null && mnScanConfig.isFullScreenScan()) {
            canvas.drawRect(0, 0, width, height, paint);
        } else {
            canvas.drawRect(0, 0, width, frame.top, paint);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
            canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
            canvas.drawRect(0, frame.bottom + 1, width, height, paint);
            //左上角
            canvas.drawRect(frame.left, frame.top, frame.left + rectW, frame.top + rectH, paintLine);
            canvas.drawRect(frame.left, frame.top, frame.left + rectH, frame.top + rectW, paintLine);
            //右上角
            canvas.drawRect(frame.right - rectW, frame.top, frame.right + 1, frame.top + rectH, paintLine);
            canvas.drawRect(frame.right - rectH, frame.top, frame.right + 1, frame.top + rectW, paintLine);
            //左下角
            canvas.drawRect(frame.left, frame.bottom - rectH, frame.left + rectW, frame.bottom + 1, paintLine);
            canvas.drawRect(frame.left, frame.bottom - rectW, frame.left + rectH, frame.bottom + 1, paintLine);
            //右下角
            canvas.drawRect(frame.right - rectW, frame.bottom - rectH, frame.right + 1, frame.bottom + 1, paintLine);
            canvas.drawRect(frame.right - rectH, frame.bottom - rectW, frame.right + 1, frame.bottom + 1, paintLine);
        }

        //文字
        canvas.drawText(hintMsg, width / 2, frame.top - CommonUtils.dip2px(context, 24), paintText);

        //中间的线：动画
        if (linePosition <= 0) {
            linePosition = frame.top + margin;
        }
        //扫描线
        if (laserStyle == MNScanConfig.LaserStyle.Line) {
            drawLineScanner(canvas, frame);
        } else if (laserStyle == MNScanConfig.LaserStyle.Grid) {
            drawGridScanner(canvas, frame);
        }
        //结果点
        drawableResultPoint(canvas);
        //动画刷新
        startAnimation();
    }

    /**
     * 绘制线性式扫描
     *
     * @param canvas
     * @param frame
     */
    private void drawLineScanner(Canvas canvas, Rect frame) {
        //线性渐变
        LinearGradient linearGradient = new LinearGradient(
                frame.left, linePosition,
                frame.left, linePosition + laserLineW,
                shadeColor(laserColor),
                laserColor,
                Shader.TileMode.MIRROR);
        paintLine.setShader(linearGradient);
        RectF rect = new RectF(frame.left + margin, linePosition, frame.right - margin, linePosition + laserLineW);
        canvas.drawOval(rect, paintLaser);
    }

    /**
     * 绘制网格式扫描
     *
     * @param canvas
     * @param frame
     */
    private void drawGridScanner(Canvas canvas, Rect frame) {
        if (gridHeight <= 0) {
            gridHeight = frame.bottom - frame.top;
        }
        int stroke = 2;
        paintLaser.setStrokeWidth(stroke);
        //计算Y轴开始位置
        int startY = gridHeight > 0 && linePosition - frame.top > gridHeight ? linePosition - gridHeight : frame.top;

        LinearGradient linearGradient = new LinearGradient(frame.left + frame.width() / 2, startY, frame.left + frame.width() / 2, linePosition, new int[]{shadeColor(laserColor), laserColor}, new float[]{0, 1f}, LinearGradient.TileMode.CLAMP);
        //给画笔设置着色器
        paintLaser.setShader(linearGradient);

        float wUnit = frame.width() * 1.0f / gridColumn;
        float hUnit = wUnit;
        //遍历绘制网格纵线
        for (int i = 1; i < gridColumn; i++) {
            canvas.drawLine(frame.left + i * wUnit, startY, frame.left + i * wUnit, linePosition, paintLaser);
        }
        int height = gridHeight > 0 && linePosition - frame.top > gridHeight ? gridHeight : linePosition - frame.top;
        //遍历绘制网格横线
        for (int i = 0; i <= height / hUnit; i++) {
            canvas.drawLine(frame.left, linePosition - i * hUnit, frame.right, linePosition - i * hUnit, paintLaser);
        }
    }

    /**
     * 处理颜色模糊
     *
     * @param color
     * @return
     */
    public int shadeColor(int color) {
        String hax = Integer.toHexString(color);
        String result = "01" + hax.substring(2);
        return Integer.valueOf(result, 16);
    }


    ValueAnimator anim;

    public void startAnimation() {
        if (anim != null && anim.isRunning()) {
            return;
        }
        anim = ValueAnimator.ofInt(frame.top + margin, frame.bottom - margin);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setDuration(2000);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                linePosition = (int) animation.getAnimatedValue();
                try {
                    postInvalidate(
                            frame.left,
                            frame.top,
                            frame.right,
                            frame.bottom);
                } catch (Exception e) {
                    postInvalidate();
                }
            }
        });
        anim.start();
    }

    public void drawViewfinder() {
        postInvalidate();
    }

    public void setResultPoint(Result result, float scaleFactor) {
        this.resultPoint = result;
        this.scaleFactor = scaleFactor;
        postInvalidate();
    }

    public void drawableResultPoint(Canvas canvas) {
        if(!showResultPoint){
           return;
        }
        if (resultPoint == null) {
            return;
        }
        ResultPoint[] points = resultPoint.getResultPoints();
        if (points == null || points.length == 0) {
            return;
        }
        if (points.length == 2 || points.length == 3 || points.length == 4) {
            //计算右上角点
            ResultPoint pointRight = points[0];
            ResultPoint pointBottom = points[0];
            float maxX = points[0].getX();
            float maxY = points[0].getY();
            for (int i = 0; i < points.length; i++) {
                ResultPoint point = points[i];
                if (maxX < point.getX()) {
                    maxX = point.getX();
                    pointRight = point;
                }
                if (maxY < point.getY()) {
                    maxY = point.getY();
                    pointBottom = point;
                }
            }
            float centerX = pointRight.getX() - (pointRight.getX() - pointBottom.getX()) / 2;
            float centerY = pointBottom.getY() - (pointBottom.getY() - pointRight.getY()) / 2;
            //判断是不是全屏模式
            if (!(mnScanConfig != null && mnScanConfig.isFullScreenScan())) {
                centerX += frame.left;
                centerY += frame.top;
            }
            paintResultPoint.setStyle(Paint.Style.STROKE);
            paintResultPoint.setColor(pointBorderColor);
            paintResultPoint.setStrokeWidth(resultPointStrokeWidth);
            RectF rect0 = new RectF(
                    centerX - resultPointRadiusCircle,
                    centerY - resultPointRadiusCircle,
                    centerX + resultPointRadiusCircle,
                    centerY + resultPointRadiusCircle);
            rect0.inset(resultPointStrokeWidth / 2, resultPointStrokeWidth / 2);
            canvas.drawRoundRect(rect0, resultPointRadiusCorners, resultPointRadiusCorners, paintResultPoint);
            paintResultPoint.setStyle(Paint.Style.FILL);
            paintResultPoint.setColor(pointColor);
            paintResultPoint.setStrokeWidth(0);
            RectF rect = new RectF(
                    centerX - resultPointRadiusCircle,
                    centerY - resultPointRadiusCircle,
                    centerX + resultPointRadiusCircle,
                    centerY + resultPointRadiusCircle);
            rect.inset(resultPointStrokeWidth, resultPointStrokeWidth);
            canvas.drawRoundRect(rect, resultPointRadiusCorners, resultPointRadiusCorners, paintResultPoint);
        }
    }


}
