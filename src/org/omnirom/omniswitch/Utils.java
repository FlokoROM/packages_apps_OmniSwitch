/*
 *  Copyright (C) 2013 The OmniROM Project
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
package org.omnirom.omniswitch;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.view.WindowManager;
import static android.view.WindowManager.DOCKED_INVALID;
import com.android.internal.statusbar.IStatusBarService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.omnirom.omniswitch.launcher.Launcher;

public class Utils {

    private static final HashSet<String> mForceUpdateKeys = new HashSet<>();
    static {
        mForceUpdateKeys.add(SettingsActivity.PREF_BG_STYLE);
        mForceUpdateKeys.add(SettingsActivity.PREF_SHOW_LABELS);
        mForceUpdateKeys.add(SettingsActivity.PREF_ICON_SIZE);
        mForceUpdateKeys.add(SettingsActivity.PREF_ICONPACK);
        mForceUpdateKeys.add(SettingsActivity.PREF_THUMB_SIZE);
        mForceUpdateKeys.add(SwitchService.DPI_CHANGE);
        mForceUpdateKeys.add(SettingsActivity.PREF_ICON_SHAPE);
        mForceUpdateKeys.add(SettingsActivity.PREF_SYSTEM_FONT);
    }

    public static void parseCollection(String listString,
            Collection<String> list) {
        if (listString.length() == 0){
            return;
        }

        if (listString.indexOf("##") == -1){
            list.add(listString);
            return;
        }
        String[] split = listString.split("##");
        for (int i = 0; i < split.length; i++) {
            list.add(split[i]);
        }
    }

    public static String flattenCollection(Collection<String> list) {
        Iterator<String> nextItem = list.iterator();
        StringBuffer buffer = new StringBuffer();
        while (nextItem.hasNext()) {
            String favorite = nextItem.next();
            buffer.append(favorite + "##");
        }
        if (buffer.length() != 0) {
            return buffer.substring(0, buffer.length() - 2).toString();
        }
        return buffer.toString();
    }

    public static String getActivityLabel(android.content.pm.PackageManager pm, Intent intent) {
        ActivityInfo ai = intent.resolveActivityInfo(pm,
                android.content.pm.PackageManager.GET_ACTIVITIES);
        String label = null;

        if (ai != null) {
            label = ai.loadLabel(pm).toString();
            if (label == null) {
                label = ai.name;
            }
        }
        return label;
    }

    public static Map<Integer, Boolean> buttonStringToMap(String buttonString, String defaultButtonString){
        Map<Integer, Boolean> buttons = new LinkedHashMap<Integer, Boolean>();
        String[] splitParts = buttonString.split(",");
        for(int i = 0; i < splitParts.length; i++){
            String[] buttonParts = splitParts[i].split(":");
            Integer key = Integer.valueOf(buttonParts[0]);
            boolean value = buttonParts[1].equals("1") ? true : false;
            buttons.put(key, value);
        }
        // add any entries that are more in the default
        String[] splitPartsDefault = defaultButtonString.split(",");
        if (splitPartsDefault.length > splitParts.length){
            for(int i = splitParts.length; i < splitPartsDefault.length; i++){
                String[] buttonParts = splitPartsDefault[i].split(":");
                Integer key = Integer.valueOf(buttonParts[0]);
                boolean value = buttonParts[1].equals("1") ? true : false;
                buttons.put(key, value);
            }
        }
        return buttons;
    }

    public static String buttonMapToString(Map<Integer, Boolean> buttons){
        String buttonString = "";
        Iterator<Integer> nextBoolean = buttons.keySet().iterator();
        while(nextBoolean.hasNext()){
            Integer key = nextBoolean.next();
            boolean value = buttons.get(key);
            if (value){
                buttonString = buttonString + key +":1,";
            } else {
                buttonString = buttonString + key + ":0,";
            }
        }
        if(buttonString.length() > 0){
            buttonString = buttonString.substring(0, buttonString.length() - 1);
        }
        return buttonString;
    }

    public static void triggerVirtualKeypress(final Handler handler, final int keyCode) {
        final InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
              keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
              KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY, InputDevice.SOURCE_CLASS_BUTTON);
        final KeyEvent upEvent = KeyEvent.changeAction(downEvent,
              KeyEvent.ACTION_UP);

        // add a small delay to make sure everything behind got focus
        handler.postDelayed(new Runnable(){
            @Override
            public void run() {
                im.injectInputEvent(downEvent,InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }}, 100);

        handler.postDelayed(new Runnable(){
            @Override
            public void run() {
                im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }}, 150);
    }

    public static void toggleImmersiveMode(Context context) {
        /*boolean immersive = Settings.System.getInt(context.getContentResolver(),
                Settings.System.IMMERSIVE_MODE, 0) == 1;

        Settings.System.putInt(context.getContentResolver(),
                Settings.System.IMMERSIVE_MODE, !immersive ? 1 : 0);*/
    }

    public static void removeFromFavorites(Context context, String item, List<String> favoriteList) {
        if (favoriteList.contains(item)){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            favoriteList.remove(item);
            prefs.edit().putString(SettingsActivity.PREF_FAVORITE_APPS,
                    Utils.flattenCollection(favoriteList)).commit();
        }
    }

    public static void addToFavorites(Context context, String item, List<String> favoriteList) {
        if (!favoriteList.contains(item)){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            favoriteList.add(item);
            prefs.edit().putString(SettingsActivity.PREF_FAVORITE_APPS,
                    Utils.flattenCollection(favoriteList)).commit();
        }
    }

    public static boolean isLockToAppEnabled(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.LOCK_TO_APP_ENABLED)
                != 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

     public static boolean isInLockTaskMode() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch(RemoteException e) {
        }
        return false;
    }

    public static boolean isMultiStackEnabled(Context context) {
        return ActivityTaskManager.supportsMultiWindow(context);
    }

    public static boolean isDockingAvailable(Context context, final List<TaskDescription> tasks) {
        for (TaskDescription ad : tasks){
            if (ad.isMultiWindowMode()) {
                return false;
            }
        }
        return true;
    }

    public static void updateFavoritesList(Context context, SwitchConfiguration config, List<String> favoriteList) {
        favoriteList.clear();
        favoriteList.addAll(config.mFavoriteList);

        /*List<String> fList = new ArrayList<String>();
        fList.addAll(favoriteList);
        Iterator<String> nextFavorite = fList.iterator();
        while (nextFavorite.hasNext()) {
            String intent = nextFavorite.next();
            PackageManager.PackageItem packageItem = PackageManager
                    .getInstance(context).getPackageItem(intent);
            if (packageItem == null) {
                favoriteList.remove(intent);
            }
        }*/
    }

    public static boolean isPrefKeyForForceUpdate(String key) {
        return mForceUpdateKeys.contains(key);
    }

    public static void enableLauncherMode(Context context, boolean value) {
        final android.content.pm.PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, Launcher.class),
                value ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
    }

    public static List<String> getFavoriteListFromStats(Context context, int count) {
        List<String> favoriteList = new ArrayList<String>();
        List<String> topLaunchList = SwitchStatistics.getInstance(context).getTopmostLaunches(count);
        for (String pkgName : topLaunchList) {
            List<String> pkgList = PackageManager.getInstance(context).getPackageListForPackageName(pkgName);
            if (pkgList.size() != 0) {
                favoriteList.addAll(pkgList);
            }
        }
        return favoriteList;
    }

    public static boolean isNycMR1OrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1;
    }

    public static boolean isNycOrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static boolean canResolveIntent(Context context, Intent intent) {
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    public static void removedFromLockedApps(Context context, String packageName, List<String> appsList) {
        if (appsList.contains(packageName)){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            appsList.remove(packageName);
            prefs.edit().putString(SettingsActivity.PREF_LOCKED_APPS_LIST,
                    TextUtils.join(",", appsList)).commit();
        }
    }

    public static void addToLockedApps(Context context, String packageName, List<String> appsList) {
        if (!appsList.contains(packageName)){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            appsList.add(packageName);
            prefs.edit().putString(SettingsActivity.PREF_LOCKED_APPS_LIST,
                    TextUtils.join(",", appsList)).commit();
        }
    }

    public static void parseLockedApps(String lockedAppsListString, List<String> appsList) {
        if (lockedAppsListString.length() == 0){
            return;
        }

        if (lockedAppsListString.indexOf(",") == -1){
            appsList.add(lockedAppsListString);
            return;
        }
        String[] split = lockedAppsListString.split(",");
        for (int i = 0; i < split.length; i++) {
            appsList.add(split[i]);
        }
    }

    public static void addToHiddenApps(Context context, String item, Collection<String> hiddenAppsList) {
        if (!hiddenAppsList.contains(item)){
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            hiddenAppsList.add(item);
            prefs.edit().putString(SettingsActivity.PREF_HIDDEN_APPS,
                    Utils.flattenCollection(hiddenAppsList)).commit();
        }
    }

    public static boolean isBrightColor(int color) {
        if (color == -3) {
            return false;
        } else if (color == Color.TRANSPARENT) {
            return false;
        } else if (color == Color.WHITE) {
            return true;
        }
        int[] rgb = { Color.red(color), Color.green(color), Color.blue(color) };
        int brightness = (int) Math.sqrt(rgb[0] * rgb[0] * .241 + rgb[1]
            * rgb[1] * .691 + rgb[2] * rgb[2] * .068);
        if (brightness >= 170) {
            return true;
        }
        return false;
    }

    public static float computeContrastBetweenColors(int bg, int fg) {
        float bgR = Color.red(bg) / 255f;
        float bgG = Color.green(bg) / 255f;
        float bgB = Color.blue(bg) / 255f;
        bgR = (bgR < 0.03928f) ? bgR / 12.92f : (float) Math.pow((bgR + 0.055f) / 1.055f, 2.4f);
        bgG = (bgG < 0.03928f) ? bgG / 12.92f : (float) Math.pow((bgG + 0.055f) / 1.055f, 2.4f);
        bgB = (bgB < 0.03928f) ? bgB / 12.92f : (float) Math.pow((bgB + 0.055f) / 1.055f, 2.4f);
        float bgL = 0.2126f * bgR + 0.7152f * bgG + 0.0722f * bgB;

        float fgR = Color.red(fg) / 255f;
        float fgG = Color.green(fg) / 255f;
        float fgB = Color.blue(fg) / 255f;
        fgR = (fgR < 0.03928f) ? fgR / 12.92f : (float) Math.pow((fgR + 0.055f) / 1.055f, 2.4f);
        fgG = (fgG < 0.03928f) ? fgG / 12.92f : (float) Math.pow((fgG + 0.055f) / 1.055f, 2.4f);
        fgB = (fgB < 0.03928f) ? fgB / 12.92f : (float) Math.pow((fgB + 0.055f) / 1.055f, 2.4f);
        float fgL = 0.2126f * fgR + 0.7152f * fgG + 0.0722f * fgB;

        return Math.abs((fgL + 0.05f) / (bgL + 0.05f));
    }

    /**
     * Creates an intent filter to listen for actions with a specific package in the data field.
     */
    public static IntentFilter getPackageFilter(String pkg, String... actions) {
        IntentFilter packageFilter = new IntentFilter();
        for (String action : actions) {
            packageFilter.addAction(action);
        }
        packageFilter.addDataScheme("package");
        packageFilter.addDataSchemeSpecificPart(pkg, PatternMatcher.PATTERN_LITERAL);
        return packageFilter;
    }

    public static boolean isPhoneVisible(Context context) {
        final Intent PHONE_INTENT = new Intent(Intent.ACTION_DIAL);
        android.content.pm.PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEPHONY)
                && pm.resolveActivity(PHONE_INTENT, 0) != null;
    }

    public static void openPhone(Context context) {
        if (!isPhoneVisible(context)) {
            return;
        }
        final TelecomManager tm = TelecomManager.from(context);
        if (tm.isInCall()) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    tm.showInCallScreen(false /* showDialpad */);
                }
            });
        } else {
            String dialer = tm.getDefaultDialerPackage();
            if (TextUtils.isEmpty(dialer)) {
                dialer = "com.android.dialer";
            }
            final Intent phoneIntent = context.getPackageManager().getLaunchIntentForPackage(dialer);
            phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(phoneIntent);
        }
    }

    public static Typeface getAppLabelFont() {
        return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    }

    public static String getDefaultBodyFont(Context context) {
        TypedArray ta = context.obtainStyledAttributes(android.R.style.TextAppearance_DeviceDefault,
                new int[]{ android.R.attr.fontFamily });
        String value = ta.getString(0);
        ta.recycle();
        return value;
    }

    public static Typeface getAppLabelFont(Context context) {
        if (SwitchConfiguration.getInstance(context).mSystemFont) {
            return Typeface.create(getDefaultBodyFont(context), Typeface.NORMAL);
        } else {
            return getAppLabelFont();
        }
    }

    public static  void toggleTorch(Context context) {
        /*IStatusBarService service = IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.toggleCameraFlash();
            } catch (RemoteException e) {
                // do nothing.
            }
        }*/
    }
}
