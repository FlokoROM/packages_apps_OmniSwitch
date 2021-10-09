/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.omnirom.omniswitch.ui;

import org.omnirom.omniswitch.R;
import org.omnirom.omniswitch.SwitchConfiguration;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;

/**
 * Utility class to override shape of {@link android.graphics.drawable.AdaptiveIconDrawable}.
 */
public class IconShapeOverride {

    private static final String TAG = "IconShapeOverride";

    public static boolean isSupported(Context context) {
        try {
            if (getSystemResField().get(null) != Resources.getSystem()) {
                // Our assumption that mSystem is the system resource is not true.
                return false;
            }
        } catch (Exception e) {
            // Ignore, not supported
            return false;
        }

        return getConfigResId() != 0;
    }

    public static void apply(Context context, String iconShape) {
        if (TextUtils.isEmpty(iconShape)) {
            return;
        }
        if (!isSupported(context)) {
            return;
        }

        // magic
        try {
            Resources override =
                    new ResourcesOverride(Resources.getSystem(), getConfigResId(), iconShape);
            getSystemResField().set(null, override);
            Log.d(TAG, "config_icon_mask = " + Resources.getSystem().getString(com.android.internal.R.string.config_icon_mask));
        } catch (Exception e) {
            Log.e(TAG, "Unable to override icon shape", e);
        }
    }

    private static Field getSystemResField() throws Exception {
        Field staticField = Resources.class.getDeclaredField("mSystem");
        staticField.setAccessible(true);
        return staticField;
    }

    private static int getConfigResId() {
        return Resources.getSystem().getIdentifier("config_icon_mask", "string", "android");
    }

    private static class ResourcesOverride extends Resources {

        private final int mOverrideId;
        private final String mOverrideValue;

        @SuppressWarnings("deprecation")
        public ResourcesOverride(Resources parent, int overrideId, String overrideValue) {
            super(parent.getAssets(), parent.getDisplayMetrics(), parent.getConfiguration());
            mOverrideId = overrideId;
            mOverrideValue = overrideValue;
        }

        @Override
        public String getString(int id) throws NotFoundException {
            if (id == mOverrideId && !TextUtils.isEmpty(mOverrideValue)) {
                return mOverrideValue;
            }
            return super.getString(id);
        }
    }
}
