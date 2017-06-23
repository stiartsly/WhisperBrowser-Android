package io.whisper.webbrowser;

import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import io.whisper.core.UserInfo;
import io.whisper.exceptions.WhisperException;

public class AgentInfoActivity extends AppCompatActivity {
	private static final String TAG = "AgentInfoActivity";

	private TextView mNameView;
	private TextView mPhoneView;
	private TextView mEmailView;
	private TextView mGenderView;
	private TextView mRegionView;
	private TextView mDescView;
	private Button   mLogoutButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_agent_info);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setTitle("本机");

		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mNameView   = (TextView)findViewById(R.id.agent_name_value);
		mPhoneView  = (TextView)findViewById(R.id.agent_phone_value);
		mEmailView  = (TextView)findViewById(R.id.agent_email_value);
		mGenderView = (TextView)findViewById(R.id.agent_gender_value);
		mRegionView = (TextView)findViewById(R.id.agent_region_value);
		mDescView   = (TextView)findViewById(R.id.agent_desc_value);

		try {
			UserInfo info = PfdAgent.singleton().getInfo();
			mNameView  .setText(info.getName());
			mPhoneView .setText(info.getPhone());
			mEmailView .setText(info.getEmail());
			mGenderView.setText(info.getGender());
			mRegionView.setText(info.getRegion());
			mDescView  .setText(info.getDescription());
		}
		catch (WhisperException e) {
			mNameView  .setText("");
			mPhoneView .setText("");
			mEmailView .setText("");
			mGenderView.setText("");
			mRegionView.setText("");
			mDescView  .setText("");
		}

		mLogoutButton = (Button) findViewById(R.id.agent_logout);
		mLogoutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				SharedPreferences preferences = getSharedPreferences("whisper", Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = preferences.edit();
				editor.remove("hasLogin");
				editor.remove("login");
				editor.remove("password");
				editor.commit();

				startActivity(new Intent(AgentInfoActivity.this, AgentLoginActivity.class));
			}
		});
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}
}
