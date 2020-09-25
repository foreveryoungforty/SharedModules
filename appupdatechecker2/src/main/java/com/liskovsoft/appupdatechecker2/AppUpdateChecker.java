package com.liskovsoft.appupdatechecker2;

import android.content.Context;
import android.net.Uri;
import com.liskovsoft.appupdatechecker2.core.AppDownloader;
import com.liskovsoft.appupdatechecker2.core.AppDownloaderListener;
import com.liskovsoft.appupdatechecker2.core.AppVersionChecker;
import com.liskovsoft.appupdatechecker2.core.AppVersionCheckerListener;
import com.liskovsoft.appupdatechecker2.other.SettingsManager;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.List;

public class AppUpdateChecker implements AppVersionCheckerListener, AppDownloaderListener {
    private static final String TAG = AppUpdateChecker.class.getSimpleName();
    private static final int MILLISECONDS_IN_MINUTE = 60_000;
    private final Context mContext;
    private final AppVersionChecker mVersionChecker;
    private final AppDownloader mDownloader;
    private final AppUpdateCheckerListener mListener;
    private final SettingsManager mSettingsManager;
    private List<String> mChangeLog;

    public AppUpdateChecker(Context context, AppUpdateCheckerListener listener) {
        Log.d(TAG, "Starting...");

        mContext = context.getApplicationContext();
        mListener = listener;
        mVersionChecker = new AppVersionChecker(mContext, this);
        mDownloader = new AppDownloader(mContext, this);
        mSettingsManager = new SettingsManager(mContext);
    }

    /**
     * You normally shouldn't need to call this, as {@link #checkForUpdates(String[] versionListUrls)} checks it before doing any updates.
     *
     * @return true if the updater should check for updates
     */
    private boolean isStale() {
        return System.currentTimeMillis() - mSettingsManager.getLastUpdatedMs() > mSettingsManager.getMinInterval() * MILLISECONDS_IN_MINUTE;
    }

    public void checkForUpdates(String updateManifestUrl) {
        checkForUpdates(new String[]{updateManifestUrl});
    }

    /**
     * Checks for updates if updates haven't been checked for recently and if checking is enabled.
     */
    public void checkForUpdates(String[] updateManifestUrls) {
        if (isEnabled() && isStale()) {
            checkForUpdatesInt(updateManifestUrls);
        }
    }

    public void forceCheckForUpdates(String updateManifestUrl) {
        forceCheckForUpdates(new String[]{updateManifestUrl});
    }

    public void forceCheckForUpdates(String[] updateManifestUrls) {
        checkForUpdatesInt(updateManifestUrls);
    }

    private void checkForUpdatesInt(String[] updateManifestUrls) {
        if (!checkPostponed()) {
            mVersionChecker.checkForUpdates(updateManifestUrls);
        }
    }

    private boolean checkPostponed() {
        return false;
    }

    @Override
    public void onChangelogReceived(boolean isLatestVersion, String latestVersionName, int latestVersionNumber, List<String> changelog, Uri[] downloadUris) {
        if (!isLatestVersion && downloadUris != null) {
            mChangeLog = changelog;
            mSettingsManager.setLatestVersionName(latestVersionName);
            mSettingsManager.setLatestVersionNumber(latestVersionNumber);

            if (latestVersionNumber == mSettingsManager.getLatestVersionNumber() &&
                FileHelpers.isFileExists(mSettingsManager.getApkPath())) {
                mListener.onUpdateFound(changelog, mSettingsManager.getApkPath());
            } else {
                mDownloader.download(downloadUris);
            }
        }
    }

    @Override
    public void onApkDownloaded(String path) {
        if (path != null) {
            mSettingsManager.setApkPath(path);

            // this line may not be executed because of json error above
            mSettingsManager.setLastUpdatedMs(System.currentTimeMillis());

            Log.d(TAG, "App update received. Apk path: " + path);
            Log.d(TAG, "App update received. Changelog: " + mChangeLog);

            mListener.onUpdateFound(mChangeLog, path);
        }
    }

    public boolean isEnabled() {
        return mSettingsManager.isEnabled();
    }

    public void setEnabled(boolean enabled) {
        mSettingsManager.setEnabled(enabled);
    }

    @Override
    public void onCheckError(Exception e) {
        mListener.onError(e);
    }

    @Override
    public void onDownloadError(Exception e) {
        mListener.onError(e);
    }

    public void installUpdate() {
        Helpers.installPackage(mContext, mSettingsManager.getApkPath());
    }
}