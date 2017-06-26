package io.whisper.webbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;

import android.os.AsyncTask;

import android.os.Bundle;
import android.util.Log;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import io.whisper.exceptions.WhisperException;

public class AgentLoginActivity extends AppCompatActivity {
	private static final String TAG = "AgentLoginActivity";

	private UserLoginTask mAuthTask = null;

	private AutoCompleteTextView mLoginView;
	private EditText mPasswordView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activiy_agent_login);

		SharedPreferences preferences = getSharedPreferences("whisper", Context.MODE_PRIVATE);
		if (preferences.getBoolean("hasLogin", false)) {
			PfdAgent.singleton().start();

			startActivity(new Intent(this, WebBrowserActivity.class));
			finish();
			return;
		}

		mLoginView = (AutoCompleteTextView) findViewById(R.id.login_username);
		mPasswordView = (EditText) findViewById(R.id.login_password);
		mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
				if (id == R.id.login_password || id == EditorInfo.IME_NULL) {
					attemptLogin();
					return true;
				}
				return false;
			}
		});

		Button loginButton = (Button) findViewById(R.id.login_button);
		loginButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				attemptLogin();
			}
		});

	}

	private void attemptLogin() {
		if (mAuthTask != null) return;

		mLoginView.setError(null);
		mPasswordView.setError(null);

		String login  = mLoginView.getText().toString();
		String password = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		if (TextUtils.isEmpty(password)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		}

		if (TextUtils.isEmpty(login)) {
			mLoginView.setError(getString(R.string.error_field_required));
			focusView = mLoginView;
			cancel = true;
		}

		if (cancel) {
			focusView.requestFocus();
		}
		else {
			mAuthTask = new UserLoginTask(login, password);
			mAuthTask.execute((Void) null);
		}
	}

	public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

		private final String mLogin;
		private final String mPassword;

		UserLoginTask(String login, String password) {
			mLogin = login;
			mPassword = password;
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			Log.i(TAG, "doInBackGroud: login: " + mLogin + " password: " + mPassword);

			try {
				 PfdAgent.singleton().checkLogin(mLogin, mPassword);
			}
			catch (WhisperException e) {
				e.printStackTrace();
				Log.e(TAG, String.format("Login failed (0x%x).", e.getErrorCode()));
				return false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(final Boolean success) {
			mAuthTask = null;

			if (success) {
				SharedPreferences preferences = getSharedPreferences("whisper", Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("hasLogin", true);
				editor.putString("login", mLogin);
				editor.putString("password", mPassword);
				editor.commit();
				
				startActivity(new Intent(AgentLoginActivity.this, WebBrowserActivity.class));
				PfdAgent.singleton().start();
				finish();
			}
			else {
				Toast.makeText(AgentLoginActivity.this, "用户名或密码错误", Toast.LENGTH_SHORT).show();
				mPasswordView.requestFocus();
			}
		}

		@Override
		protected void onCancelled() {
			mAuthTask = null;
		}
	}
}

