package io.whisper.webbrowser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import android.util.Log;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebBrowserActivity extends AppCompatActivity  {
	private static final String TAG = "WebBrowserActivity";

	private WebView mWebView;

	private void loadUrl(String url) {
		Log.i(TAG, "Loading url " + url + " ...");
		mWebView.loadUrl(url);
	}

	private void showFailure(String error) {
		String data = "<h1>" + error + "</h1>";
		mWebView.loadData(data, "text/html; charset=UTF-8", null);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_web_browser);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setTitle("");
		setSupportActionBar(toolbar);

		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.setWebViewClient(new WebViewClient());

		WebSettings settings = mWebView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);

		IntentFilter filter = new IntentFilter();
		filter.addAction(PfdAgent.ACTION_AGENT_STATUS_CHANGED);
		filter.addAction(PfdServer.ACTION_SERVER_STATUS_CHANGED);
		registerReceiver(broadcastReceiver, filter);

		Log.i(TAG, "Registered broadcast");

		if (PfdAgent.singleton().isReady()) {
			PfdServer server = PfdAgent.singleton().getCheckedServer();
			if (server != null) {
				server.setupPortforwarding();
			}
			else {
				showFailure("No server available");
			}
		}
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "Received intent with action: " + intent.getAction());

			//String reason = intent.getExtras().getString("reason");
			String reason = "Agent error";
			int status = intent.getExtras().getInt("status");

			if (intent.getAction() == PfdAgent.ACTION_AGENT_STATUS_CHANGED)  {
				if (status == 0) {
					PfdServer server = PfdAgent.singleton().getCheckedServer();
					if (server != null) {
						server.setupPortforwarding();
					}
					else {
						showFailure("No server available");
					}
				}
				else {
					//showFailure("代理错误");
					showFailure(reason);
				}

			}
			else if (intent.getAction() == PfdServer.ACTION_SERVER_STATUS_CHANGED) {
				if (status == 0) {
					PfdServer server = PfdAgent.singleton().getCheckedServer();
					if (server != null) {
						String host = server.getHost();
						String port = server.getPort();
						String url = "http://" + host + ":" + port;

						Log.i(TAG, "URL: " + url);
						loadUrl(url);
					}
					else {
						showFailure("No server available");
					}
				}
				else {
					showFailure(reason);
				}
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_myinfo, menu);
		getMenuInflater().inflate(R.menu.menu_home, menu);
		getMenuInflater().inflate(R.menu.menu_reload, menu);
		getMenuInflater().inflate(R.menu.menu_list_server, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_list_server) {
			startActivity(new Intent(this, ServerListActivity.class));
		}
		else if (id == R.id.action_home_page) {
			String port = PfdAgent.singleton().getCheckedServer().getPort();
			loadUrl("http://127.0.0.1:" + port);
		}
		else if (id == R.id.action_reload) {
			mWebView.reload();
		}
		else if (id == R.id.action_myinfo) {
			startActivity(new Intent(WebBrowserActivity.this, AgentInfoActivity.class));
		}
		else {
			return super.onOptionsItemSelected(item);
		}

		return true;
	}
}
