/*
 *  Copyright (C) 2013-2015 The OmniROM Project
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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.omnirom.omniswitch.ui.ISwitchLayout;
import org.omnirom.omniswitch.ui.SwitchGestureView;
import org.omnirom.omniswitch.ui.SwitchLayout;
import org.omnirom.omniswitch.ui.SwitchLayoutVertical;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityManager;
import android.app.TaskStackBuilder;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.RemoteException;
import android.os.PowerManager;
import android.os.IPowerManager;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class SwitchManager {
    private static final String TAG = "OmniSwitch:SwitchManager";
    private static final boolean DEBUG = false;
    private List<TaskDescription> mLoadedTasks;
    private List<TaskDescription> mLoadedTasksOriginal;
    private ISwitchLayout mLayout;
    private SwitchGestureView mGestureView;
    private Context mContext;
    private SwitchConfiguration mConfiguration;
    private int mLayoutStyle;
    private final ActivityManager mAm;
    private final IActivityManager mIAm;
    private IPowerManager mPowerService;

    public SwitchManager(Context context, int layoutStyle) {
        mContext = context;
        mConfiguration = SwitchConfiguration.getInstance(mContext);
        mLayoutStyle = layoutStyle;
        mAm = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mPowerService = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
        mIAm = ActivityManager.getService();
        init();
    }

    public void hide(boolean fast) {
        if (isShowing()) {
            if (DEBUG){
                Log.d(TAG, "hide");
            }
            mLayout.hide(fast);
        }
    }

    public void hideHidden() {
        if (isShowing()) {
            if (DEBUG){
                Log.d(TAG, "hideHidden");
            }
            mLayout.hideHidden();
        }
    }

    public void show() {
        if (!isShowing()) {
            if (DEBUG){
                Log.d(TAG, "show");
            }
            startBoost();
            mLayout.setHandleRecentsUpdate(true);

            clearTasks();
            RecentTasksLoader.getInstance(mContext).cancelLoadingTasks();
            RecentTasksLoader.getInstance(mContext).setSwitchManager(this);
            RecentTasksLoader.getInstance(mContext).loadTasksInBackground(0, true, true);

            // show immediately
            mLayout.show();
        }
    }

    public void showPreloaded() {
        if (!isShowing()) {
            if (DEBUG){
                Log.d(TAG, "showPreloaded");
            }

            // show immediately
            mLayout.show();
        }
    }

    public void beforePreloadTasks() {
        if (!isShowing()) {
            if (DEBUG){
                Log.d(TAG, "beforePreloadTasks");
            }
            startBoost();
            clearTasks();
            mLayout.setHandleRecentsUpdate(true);
        }
    }

    public void showHidden() {
        if (!isShowing()) {
            if (DEBUG){
                Log.d(TAG, "showHidden");
            }
            mLayout.setHandleRecentsUpdate(true);

            // show immediately
            mLayout.showHidden();
        }
    }

    public boolean isShowing() {
        return mLayout.isShowing();
    }

    private void init() {
        if (DEBUG){
            Log.d(TAG, "init");
        }

        mLoadedTasks = new ArrayList<TaskDescription>();
        mLoadedTasksOriginal = new ArrayList<TaskDescription>();
        switchLayout();
        mGestureView = new SwitchGestureView(this, mContext);
    }

    public void killManager() {
        RecentTasksLoader.killInstance();
        mGestureView.hide();
    }

    public ISwitchLayout getLayout() {
        return mLayout;
    }

    private void switchLayout() {
        if (mLayout != null) {
            mLayout.shutdownService();
        }
        if (mLayoutStyle == 0) {
            mLayout = new SwitchLayout(this, mContext);
        } else {
            mLayout = new SwitchLayoutVertical(this, mContext);
        }

    }
    public SwitchGestureView getSwitchGestureView() {
        return mGestureView;
    }

    public void update(List<TaskDescription> taskList, List<TaskDescription> taskListOriginal) {
        mLoadedTasksOriginal = taskListOriginal;
        mLoadedTasks.clear();
        mLoadedTasks.addAll(taskList);
        mLayout.update();
        mGestureView.update();
    }

    public void switchTask(TaskDescription ad, boolean close, boolean customAnim) {
        if (ad.isKilled()) {
            return;
        }

        if(close){
            hide(true);
        }
        try {
            ActivityOptions options = null;
            if (customAnim) {
                options = ActivityOptions.makeCustomAnimation(mContext, R.anim.last_app_in, R.anim.last_app_out);
            } else {
                options = ActivityOptions.makeBasic();
            }
            ActivityManagerNative.getDefault().startActivityFromRecents(
                        ad.getPersistentTaskId(),  options.toBundle());
            SwitchStatistics.getInstance(mContext).traceStartIntent(ad.getIntent());
            if (DEBUG){
                Log.d(TAG, "switch to " + ad.getLabel());
            }
        } catch (Exception e) {
        }
    }

    public void killTask(TaskDescription ad, boolean close) {
        if (mConfiguration.mRestrictedMode){
            return;
        }

        if(close){
            hide(false);
        }

        if (ad.isLocked()) {
            // remove from locked
            toggleLockedApp(ad, ad.isLocked(), false);
        }

        removeTask(ad.getPersistentTaskId());
        if (DEBUG){
            Log.d(TAG, "kill " + ad.getLabel());
        }

        if (!close) {
            ad.setKilled();
            removeTaskFromList(ad);
            mLayout.refresh();
        }
    }

    /**
     * killall will always remove all tasks - also those that are
     * filtered out (not active)
     * @param close
     */
    public void killAll(boolean close) {
        if (mConfiguration.mRestrictedMode){
            return;
        }

        if (mLoadedTasksOriginal.size() == 0) {
            if(close){
                hide(true);
            }
            return;
        }

        Iterator<TaskDescription> nextTask = mLoadedTasksOriginal.iterator();
        while (nextTask.hasNext()) {
            TaskDescription ad = nextTask.next();
            if (ad.isLocked()) {
                continue;
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG){
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        goHome(close);
    }

    public void killOther(boolean close) {
        if (mConfiguration.mRestrictedMode){
            return;
        }

        if (mLoadedTasksOriginal.size() <= 1) {
            if(close){
                hide(true);
            }
            return;
        }
        Iterator<TaskDescription> nextTask = mLoadedTasksOriginal.iterator();
        // skip active task
        nextTask.next();
        while (nextTask.hasNext()) {
            TaskDescription ad = nextTask.next();
            if (ad.isLocked()) {
                continue;
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG){
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        if(close){
            hide(true);
        }
    }

    public void killCurrent(boolean close) {
        if (mConfiguration.mRestrictedMode){
            return;
        }

        if (mLoadedTasksOriginal.size() == 0) {
            if(close){
                hide(true);
            }
            return;
        }

        if (mLoadedTasksOriginal.size() >= 1){
            TaskDescription ad = mLoadedTasksOriginal.get(0);
            if (ad.isLocked()) {
                // remove from locked
                toggleLockedApp(ad, ad.isLocked(), false);
            }
            removeTask(ad.getPersistentTaskId());
            if (DEBUG){
                Log.d(TAG, "kill " + ad.getLabel());
            }
            ad.setKilled();
        }
        if(close){
            hide(true);
        }
    }

    public void goHome(boolean close) {
        if(close){
            hide(true);
        }

        Intent homeIntent = new Intent(Intent.ACTION_MAIN, null);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mContext.startActivity(homeIntent);
    }

    public void updatePrefs(SharedPreferences prefs, String key) {
        if (key != null && key.equals(SettingsActivity.PREF_LAYOUT_STYLE)) {
            String layoutStyle = prefs.getString(SettingsActivity.PREF_LAYOUT_STYLE, "1");
            mLayoutStyle = Integer.valueOf(layoutStyle);
            switchLayout();
        }
        mLayout.updatePrefs(prefs, key);
        mGestureView.updatePrefs(prefs, key);
    }

    public void toggleLastApp(boolean close) {
        if (mLoadedTasksOriginal.size() < 2) {
            if(close){
                hide(true);
            }
            return;
        }

        TaskDescription ad = mLoadedTasksOriginal.get(1);
        switchTask(ad, close, true);
    }

    public void startIntentFromtString(String intent, boolean close) {
        try {
            Intent intentapp = Intent.parseUri(intent, 0);
            ActivityInfo info = mContext.getPackageManager().getActivityInfo(intentapp.getComponent(), 0);
            if (info != null && DEBUG) {
                Log.d(TAG, "startIntentFromtString resizable = "  + info.resizeMode);
            }
            if(close){
                hide(true);
            }
            startIntentFromtString(mContext, intent);
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + intent + "]");
        } catch (NameNotFoundException e){
            Log.e(TAG, "NameNotFoundException: [" + intent + "]");
        }
    }

    public static void startIntentFromtString(Context context, String intent) {
        try {
            Intent intentapp = Intent.parseUri(intent, 0);
            SwitchStatistics.getInstance(context).traceStartIntent(intentapp);
            context.startActivity(intentapp);
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + intent + "]");
        } catch (ActivityNotFoundException e){
            Log.e(TAG, "ActivityNotFound: [" + intent + "]");
        }
    }

    public void updateLayout(int height) {
        if (mLayout.isShowing()){
            mLayout.updateLayout();
        }
        if (mGestureView.isShowing()){
            mGestureView.updateDragHandlePosition(height);
        }
    }

    public void startApplicationDetailsActivity(String packageName) {
        hide(true);
        startApplicationDetailsActivity(mContext, packageName);
    }

    public static void startApplicationDetailsActivity(Context context, String packageName) {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(context.getPackageManager()));
        TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent).startActivities();
    }

    public void startSettingsActivity() {
        hide(true);
        startSettingsActivity(mContext);
    }

    public static void startSettingsActivity(Context context) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS, null);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(intent);
    }

    public void startOmniSwitchSettingsActivity() {
        hide(true);
        startOmniSwitchSettingsActivity(mContext);
    }

    public static void startOmniSwitchSettingsActivity(Context context) {
        Intent mainActivity = new Intent(context,
                SettingsActivity.class);
        mainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(mainActivity);
    }

    public static void startPlaceholderActivity(Context context) {
        Intent mainActivity = new Intent(context,
                PlaceholderActivity.class);
        mainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(mainActivity);
    }

    public void shutdownService() {
        mLayout.shutdownService();
    }

    public void slideLayout(float distanceX) {
        mLayout.slideLayout(distanceX);
    }

    public void finishSlideLayout() {
        mLayout.finishSlideLayout();
    }

    public void openSlideLayout(boolean fromFling) {
        mLayout.openSlideLayout(fromFling);
    }

    public void canceSlideLayout() {
        mLayout.canceSlideLayout();
    }

    public List<TaskDescription> getTasks() {
	    return mLoadedTasks;
    }

    public void clearTasks() {
        mLoadedTasks.clear();
        mLoadedTasksOriginal.clear();
        mLayout.notifiyRecentsListChanged();
    }

    private TaskDescription getCurrentTopTask() {
        if (mLoadedTasksOriginal.size() >= 1) {
            TaskDescription ad = mLoadedTasksOriginal.get(0);
            return ad;
        } else {
            return null;
        }
    }

    public void lockToCurrentApp(boolean close) {
        TaskDescription ad = getCurrentTopTask();
        if (ad != null) {
            lockToApp(ad, close);
        }
    }

    public void lockToApp(TaskDescription ad, boolean close) {
        try {
            if (!ActivityTaskManager.getService().isInLockTaskMode()) {
                switchTask(ad, false, false);
                ActivityTaskManager.getService().startSystemLockTaskMode(ad.getPersistentTaskId());
                if (DEBUG){
                    Log.d(TAG, "lock app " + ad.getLabel() + " " + ad.getPersistentTaskId());
                }
            }
        } catch(RemoteException e) {
        }
        if(close){
            hide(true);
        }
    }

    public void stopLockToApp(boolean close) {
        try {
            if (ActivityTaskManager.getService().isInLockTaskMode()) {
                ActivityTaskManager.getService().stopSystemLockTaskMode();
                if (DEBUG){
                    Log.d(TAG, "stop lock app");
                }
            }
        } catch(RemoteException e) {
        }
        if(close){
            hide(true);
        }
    }

    public void toggleLockToApp(boolean close) {
        try {
            if (ActivityTaskManager.getService().isInLockTaskMode()) {
                stopLockToApp(false);
            } else {
                lockToCurrentApp(false);
            }
        } catch(RemoteException e) {
        }
        if(close){
            hide(true);
        }
    }

    public void forceStop(TaskDescription ad, boolean close) {
        if (mConfiguration.mRestrictedMode){
            return;
        }

        if(close){
            hide(false);
        }

        mAm.forceStopPackage(ad.getPackageName());
        if (DEBUG){
            Log.d(TAG, "forceStop " + ad.getLabel());
        }

        if (!close) {
            ad.setKilled();
            removeTaskFromList(ad);
            mLayout.refresh();
        }
    }

    public void toggleLockedApp(TaskDescription ad, boolean isLockedApp, boolean refresh) {
        List<String> lockedAppsList = mConfiguration.mLockedAppList;
        if (DEBUG){
            Log.d(TAG, "toggleLockedApp " + lockedAppsList);
        }
        if (isLockedApp) {
            Utils.removedFromLockedApps(mContext, ad.getPackageName(), lockedAppsList);
        } else {
            Utils.addToLockedApps(mContext, ad.getPackageName(), lockedAppsList);
        }
        ad.setLocked(!isLockedApp);
        ad.setNeedsUpdate(true);
        if (refresh) {
            mLayout.refresh();
        }
    }

    private void removeTaskFromList(TaskDescription ad) {
        mLoadedTasks.remove(ad);
        mLoadedTasksOriginal.remove(ad);
        mLayout.notifiyRecentsListChanged();
    }

    public void startBoost() {
        if (mConfiguration.mUsePowerHint) {
            /*try {
                // TODO 8 is the POWER_HINT_LAUNCH but there is no way to access this from here
                mPowerService.powerHint(8, 1);
            } catch (RemoteException e){
            }*/
        }
    }

    public void stopBoost() {
        if (mConfiguration.mUsePowerHint) {
            /*try {
                // TODO 8 is the POWER_HINT_LAUNCH
                mPowerService.powerHint(8, 0);
            } catch (RemoteException e){
            }*/
        }
    }

    private void removeTask(int taskid) {
        try {
            mIAm.removeTask(taskid);
        } catch (RemoteException e) {
            Log.e(TAG, "removeTask failed", e);
        }
    }

    public void dockTask(TaskDescription ad, boolean close) {
        if (ad.isKilled()) {
            return;
        }

        if(close){
            hide(true);
        }
        try {
            ActivityOptions options = makeSplitScreenOptions(true);
            ActivityManagerNative.getDefault().startActivityFromRecents(
                        ad.getPersistentTaskId(), options.toBundle());
            if (DEBUG){
                Log.d(TAG, "dock task " + ad.getLabel());
            }
        } catch (Exception e) {
        }
    }

    private ActivityOptions makeSplitScreenOptions(boolean dockTopLeft) {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        return options;
    }
}
