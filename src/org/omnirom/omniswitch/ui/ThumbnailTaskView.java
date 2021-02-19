/*
 *  Copyright (C) 2017 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omniswitch.ui;

import org.omnirom.omniswitch.SwitchConfiguration;
import org.omnirom.omniswitch.TaskDescription;
import org.omnirom.omniswitch.ThumbnailData;
import org.omnirom.omniswitch.Utils;
import org.omnirom.omniswitch.RecentTasksLoader;
import org.omnirom.omniswitch.R;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.os.Handler;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;

public class ThumbnailTaskView extends View implements TaskDescription.ThumbChangeListener {
    private static final String TAG = "ThumbnailTaskView";
    private static boolean DEBUG = false;
    private String mIntent;
    private TaskDescription mTask;
    private Runnable mAction;
    private Handler mHandler = new Handler();
    private boolean mCanSideHeader;
    private float mThumbRatio = 1.0f;
    private static Bitmap sDefaultThumb;
    private SwitchConfiguration mConfiguration;

    public ThumbnailTaskView(Context context) {
        super(context);
        mConfiguration = SwitchConfiguration.getInstance(context);
    }

    public void setIntent(String intent) {
        mIntent = intent;
    }

    public String getIntent() {
        return mIntent;
    }

    public TaskDescription getTask() {
        return mTask;
    }

    public void setTask(TaskDescription task, boolean reload) {
        mTask = task;
        mTask.setThumbChangeListener(this);
        ThumbnailData thumb = getTask().getThumb();
        Drawable icon = getTask().getIcon();

        if (thumb == null || icon == null || reload) {
            RecentTasksLoader.getInstance(getContext()).loadTaskInfo(getTask());
            loadTaskThumb();
            invalidate();
        } else {
            updateThumb();
        }
    }

    public void runAction() {
        if (mAction != null) {
            mAction.run();
        }
    }

    public void setAction(Runnable action) {
        mAction = action;
    }

    public boolean isAction() {
        return mAction != null;
    }

    @Override
    public String toString() {
        return getLabel().toString();
    }

    private void updateThumb() {
        if (getTask() != null){
            if (DEBUG) {
                Log.d(TAG, "updateThumb: " + getLabel()
                        + " " + getTask().getPersistentTaskId());
            }
            invalidate();
        }
    }

    @Override
    public void thumbChanged(final int persistentTaskId) {
        if (getTask() != null){
            if (persistentTaskId == getTask().getPersistentTaskId()) {
                mHandler.post(new Runnable(){
                    @Override
                    public void run() {
                        updateThumb();
                    }});
            }
        }
    }

    @Override
    public int getPersistentTaskId() {
        if (getTask() != null){
            return getTask().getPersistentTaskId();
        }
        return -1;
    }

    public void setCanSideHeader(boolean mCanSideHeader) {
        this.mCanSideHeader = mCanSideHeader;
    }

    private void loadTaskThumb() {
        if (getTask() != null) {
            if (DEBUG) {
                Log.d(TAG, "loadTaskThumb new:" + getLabel()
                        + " " + getTask().getPersistentTaskId());
            }
            RecentTasksLoader.getInstance(mContext).loadThumbnail(getTask());
        }
    }

    public void setThumbRatio(float thumbRatio) {
        mThumbRatio = thumbRatio;
    }

    private Bitmap getDefaultThumb() {
        if (sDefaultThumb == null) {
            sDefaultThumb = RecentTasksLoader.getInstance(getContext()).getDefaultThumb();
        }
        return sDefaultThumb;
    }

    private Bitmap getThumb() {
        if (getTask() != null) {
            ThumbnailData thumb = getTask().getThumb();
            if (thumb != null) {
                return thumb.thumbnail;
            }
        }
        return getDefaultThumb();
    }

    private String getLabel() {
        if (getTask() != null) {
            return getTask().getLabel();
        }
        return null;
    }

    private Drawable getIcon() {
        if (getTask() != null) {
            Drawable d = getTask().getIcon();
            if (d != null) {
                return BitmapUtils.resize(getContext().getResources(), d,
                        mConfiguration.mOverlayIconSizeDp, 0, mConfiguration.mDensity);
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int iconSizePx = Math.round(mConfiguration.mOverlayIconSizeDp * mConfiguration.mDensity);
        final int textInsetPx = Math.round(5 * mConfiguration.mDensity);
        final int width = (int)(mConfiguration.mThumbnailWidth * mThumbRatio);
        final int height = (int)(mConfiguration.mThumbnailHeight * mThumbRatio);
        final boolean sideHeader = mCanSideHeader ? mConfiguration.mSideHeader : false;
        final int iconBorderSizePx = mConfiguration.getOverlayHeaderWidth();
        Resources resources = getContext().getResources();

        canvas.setHwBitmapsInSwModeEnabled(true);
        Bitmap taskThumb = getThumb();
        taskThumb.prepareToDraw();
        Drawable taskIcon = getIcon();

        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(sideHeader ? width + iconBorderSizePx : width,
                sideHeader ? height : height + iconBorderSizePx,
                Bitmap.Config.ARGB_8888);

        int bSize = taskThumb.getWidth() > taskThumb.getHeight() ? taskThumb.getHeight() : taskThumb.getWidth();
        Rect src = new Rect(0, 0, bSize, bSize);
        Rect dest = new Rect(sideHeader ? iconBorderSizePx : 0, sideHeader ? 0 : iconBorderSizePx,
                width + (sideHeader ? iconBorderSizePx : 0), height + (sideHeader ? 0 : iconBorderSizePx));
        canvas.drawBitmap(taskThumb, src, dest, null);

        final TextPaint textPaint = BitmapUtils.getLabelTextPaint(getContext());
        final int startTextPx = iconBorderSizePx + textInsetPx;
        final int textSize = Math.round(14 * mConfiguration.mDensity);
        textPaint.setTextSize(textSize);

        Paint bgPaint = null;
        if (getTask().isLocked()) {
            bgPaint = BitmapUtils.getLockedAppsPaint(resources);
            textPaint.setColor(Color.WHITE);
        } else if (mConfiguration.mColorfulHeader && getTask().getTaskPrimaryColor() != 0) {
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(getTask().getTaskPrimaryColor());
            textPaint.setColor(getTask().useLightOnPrimaryColor() ? Color.WHITE : Color.BLACK);
        } else {
            bgPaint = BitmapUtils.getDefaultBgPaint(resources, mConfiguration);
            textPaint.setColor(mConfiguration.getCurrentTextTint(bgPaint.getColor()));
        }
        if (bgPaint != null) {
            if (mConfiguration.mBgStyle != SwitchConfiguration.BgStyle.TRANSPARENT) {
                bgPaint.setAlpha(255);
            } else {
                bgPaint.setAlpha((int) (255 * mConfiguration.mBackgroundOpacity));
            }
            if (sideHeader)  {
                canvas.drawRect(0, 0, iconBorderSizePx, height, bgPaint);
            } else {
                canvas.drawRect(0, 0, width, iconBorderSizePx, bgPaint);
            }
        }
        if (taskIcon != null) {
            final int iconInset = (iconBorderSizePx - iconSizePx) / 2;
            taskIcon.setBounds(iconInset, iconInset, iconSizePx + iconInset, iconSizePx + iconInset);
            taskIcon.draw(canvas);
        }
        if (getLabel() != null && mConfiguration.mShowLabels) {
            String label = TextUtils.ellipsize(getLabel(), textPaint, width - startTextPx - textInsetPx, TextUtils.TruncateAt.END).toString();
            if (sideHeader) {
                canvas.save();
                int xPos = (int) ((iconBorderSizePx / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)) ; 
                int yPos = height - textInsetPx;
                canvas.rotate(270, xPos, yPos);
                canvas.drawText(label, xPos, yPos, textPaint);
                canvas.restore();
            } else {
                int yPos = (int) ((iconBorderSizePx / 2) - ((textPaint.descent() + textPaint.ascent()) / 2)) ; 
                int xPos = startTextPx;
                canvas.drawText(label, xPos, yPos, textPaint);
            }
        }
    }
}
