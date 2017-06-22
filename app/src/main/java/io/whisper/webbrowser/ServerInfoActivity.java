package io.whisper.webbrowser;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import io.whisper.exceptions.WhisperException;

public class ServerInfoActivity extends AppCompatActivity {
	private static final String TAG = "ServerInfoActivity";
	private PfdServer mServer;

	private TextView mServerIdView;
	private TextView mNameView;
	private TextView mServiceView;

	private Button mDeletionButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server_info);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setTitle("服务节点信息");

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		String serverId = getIntent().getStringExtra("serverId");
		mServer = PfdAgent.singleton().getServer(serverId);

		mServerIdView = (TextView) findViewById(R.id.server_id_value);
		mNameView     = (TextView) findViewById(R.id.server_name_value);
		mServiceView  = (TextView) findViewById(R.id.server_service_value);

		if (mServer != null) {
			mServerIdView.setText(mServer.getServerId());
			mNameView    .setText(mServer.getName());
			mServiceView .setText(mServer.getServiceName());

			mServiceView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					mServer.setServiceName(((TextView)view).getText().toString());
				}
			});
		}

		mDeletionButton = (Button)findViewById(R.id.server_deletion);
		mDeletionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				removeServer();
			}
		});

	}

	private void removeServer() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("确定要删除此服务节点?");
		builder.setPositiveButton(R.string.remove,  new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				try {
					PfdAgent.singleton().unpairServer(mServer.getServerId());
					finish();
				} catch (WhisperException e) {
					e.printStackTrace();
					Toast.makeText(ServerInfoActivity.this, "删除服务节点失败", Toast.LENGTH_SHORT).show();
				}
			}
		});

		builder.setNegativeButton(R.string.cancel, null);
		builder.show();
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}
}
