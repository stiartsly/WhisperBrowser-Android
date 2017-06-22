package io.whisper.webbrowser;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ContentResolver;
import android.view.Window;
import android.view.WindowManager;

public class WebBrowserApp extends Application {
	private static Context mContext;
	private static ContentResolver mContentResolver;
	private static Activity mCurActivity = null;

	public void onCreate() {
		super.onCreate();

		mContext = getApplicationContext();
		mContentResolver = getContentResolver();

		this.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
			@Override
			public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

			}

			@Override
			public void onActivityStarted(Activity activity) {
			}

			@Override
			public void onActivityResumed(Activity activity) {
				mCurActivity = activity;
			}

			@Override
			public void onActivityPaused(Activity activity) {
				if (mCurActivity == activity)
					mCurActivity = null;
			}

			@Override
			public void onActivityStopped(Activity activity) {

			}

			@Override
			public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

			}

			@Override
			public void onActivityDestroyed(Activity activity) {
				if (mCurActivity == activity)
					mCurActivity = null;
			}
		});
	}

	public static Context getAppContext() {
		return WebBrowserApp.mContext;
	}

	public static ContentResolver getAppContentResolver() {
		return WebBrowserApp.mContentResolver;
	}
}
