package com.muzima.view.login;

import android.content.*;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.internal.nineoldandroids.animation.ValueAnimator;
import com.muzima.MuzimaApplication;
import com.muzima.R;
import com.muzima.domain.Credentials;
import com.muzima.service.MuzimaSyncService;
import com.muzima.utils.NetworkUtils;
import com.muzima.view.MainActivity;
import com.muzima.view.cohort.CohortPrefixWizardActivity;

import static com.muzima.utils.Constants.DataSyncServiceConstants.SyncStatusConstants.AUTHENTICATION_SUCCESS;

public class LoginActivity extends SherlockActivity {
    private static final String TAG = "LoginActivity";
    public static final String isFirstLaunch = "isFirstLaunch";
    private EditText serverUrlText;
    private EditText usernameText;
    private EditText passwordText;
    private Button loginButton;
    private BackgroundAuthenticationTask backgroundAuthenticationTask;
    private TextView noConnectivityText;
    private TextView authenticatingText;

    private ValueAnimator flipFromNoConnToLoginAnimator;
    private ValueAnimator flipFromLoginToNoConnAnimator;
    private ValueAnimator flipFromLoginToAuthAnimator;
    private ValueAnimator flipFromAuthToLoginAnimator;
    private ValueAnimator flipFromAuthToNoConnAnimator;
    private boolean honeycombOrGreater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            honeycombOrGreater = true;
        }

        initViews();
        setupListeners();
        initAnimators();

        boolean isFirstLaunch = getIntent().getBooleanExtra(LoginActivity.isFirstLaunch, true);
        if (!isFirstLaunch) {
            removeServerUrlAsInput();
            useSavedServerUrl();
        }
        passwordText.setTypeface(Typeface.DEFAULT); //Hack to get it to use default font space.
    }

    private void removeServerUrlAsInput() {
        serverUrlText.setVisibility(View.GONE);
        findViewById(R.id.server_url_divider).setVisibility(View.GONE);
    }

    private void useSavedServerUrl() {
        Credentials credentials;
        credentials = new Credentials(this);
        serverUrlText.setText(credentials.getServerUrl());
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupStatusView();

        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityChangeReceiver, intentFilter);
    }

    private void setupStatusView() {
        if (!NetworkUtils.isConnectedToNetwork(this)) {
            if (backgroundAuthenticationTask != null) {
                backgroundAuthenticationTask.cancel(true);
            }
            noConnectivityText.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.GONE);
            authenticatingText.setVisibility(View.GONE);
        } else if (backgroundAuthenticationTask != null && backgroundAuthenticationTask.getStatus() == AsyncTask.Status.RUNNING) {
            noConnectivityText.setVisibility(View.GONE);
            loginButton.setVisibility(View.GONE);
            authenticatingText.setVisibility(View.VISIBLE);
        } else {
            noConnectivityText.setVisibility(View.GONE);
            loginButton.setVisibility(View.VISIBLE);
            authenticatingText.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(connectivityChangeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backgroundAuthenticationTask != null) {
            backgroundAuthenticationTask.cancel(true);
        }
    }

    private void setupListeners() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validInput()) {
                    if (backgroundAuthenticationTask != null && backgroundAuthenticationTask.getStatus() == AsyncTask.Status.RUNNING) {
                        Toast.makeText(getApplicationContext(), "Authentication in progress...", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    backgroundAuthenticationTask = new BackgroundAuthenticationTask();
                    backgroundAuthenticationTask.execute(
                            new Credentials(serverUrlText.getText().toString(), usernameText.getText().toString(),
                                    passwordText.getText().toString()
                            ));
                } else {
                    int errorColor = getResources().getColor(R.color.error_text_color);
                    if (serverUrlText.getText().toString().isEmpty()) {
                        serverUrlText.setHint("Please Enter Server URL");
                        serverUrlText.setHintTextColor(errorColor);
                    }

                    if (usernameText.getText().toString().isEmpty()) {
                        usernameText.setHint("Please Enter Username");
                        usernameText.setHintTextColor(errorColor);
                    }

                    if (passwordText.getText().toString().isEmpty()) {
                        passwordText.setHint("Please Enter Password");
                        passwordText.setHintTextColor(errorColor);
                    }
                }
            }
        });
    }

    private boolean validInput() {
        if (serverUrlText.getText().toString().isEmpty()
                || usernameText.getText().toString().isEmpty()
                || passwordText.getText().toString().isEmpty()) {
            return false;
        }
        return true;
    }

    private void initViews() {
        serverUrlText = (EditText) findViewById(R.id.serverUrl);
        usernameText = (EditText) findViewById(R.id.username);
        passwordText = (EditText) findViewById(R.id.password);
        loginButton = (Button) findViewById(R.id.login);
        noConnectivityText = (TextView) findViewById(R.id.noConnectionText);
        authenticatingText = (TextView) findViewById(R.id.authenticatingText);
    }

    private class BackgroundAuthenticationTask extends AsyncTask<Credentials, Void, BackgroundAuthenticationTask.Result> {

        @Override
        protected void onPreExecute() {
            if (loginButton.getVisibility() == View.VISIBLE) {
                if (honeycombOrGreater) {
                    flipFromLoginToAuthAnimator.start();
                }
            }
        }

        @Override
        protected Result doInBackground(Credentials... params) {
            Credentials credentials = params[0];
            MuzimaSyncService muzimaSyncService = ((MuzimaApplication) getApplication()).getMuzimaSyncService();
            int authenticationStatus = muzimaSyncService.authenticate(credentials.getCredentialsArray());
            return new Result(credentials, authenticationStatus);
        }

        @Override
        protected void onPostExecute(Result result) {
            if (result.status == AUTHENTICATION_SUCCESS) {
                saveCredentials(result.credentials);

                startNextActivity();
            } else {
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
                if (honeycombOrGreater) {
                    if (authenticatingText.getVisibility() == View.VISIBLE || flipFromLoginToAuthAnimator.isRunning()) {
                        flipFromLoginToAuthAnimator.cancel();
                        flipFromAuthToLoginAnimator.start();
                    }
                }
            }

        }

        private void startNextActivity() {
            Intent intent;
            if (isWizardFinished()) {
                intent = new Intent(getApplicationContext(), MainActivity.class);
            } else {
                intent = new Intent(getApplicationContext(), CohortPrefixWizardActivity.class);
            }
            startActivity(intent);
            finish();
        }

        private boolean isWizardFinished() {
            return false;
//            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
//            String wizardFinishedKey = getResources().getString(R.string.preference_wizard_finished);
//
//            return settings.getBoolean(wizardFinishedKey, false);
        }

        private void saveCredentials(Credentials credentials) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String usernameKey = getResources().getString(R.string.preference_username);
            String passwordKey = getResources().getString(R.string.preference_password);
            String serverKey = getResources().getString(R.string.preference_server);

            settings.edit()
                    .putString(usernameKey, credentials.getUserName())
                    .putString(passwordKey, credentials.getPassword())
                    .putString(serverKey, credentials.getServerUrl())
                    .commit();
        }

        protected class Result {
            Credentials credentials;
            int status;

            private Result(Credentials credentials, int status) {
                this.credentials = credentials;
                this.status = status;
            }
        }
    }

    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean connectedToNetwork = NetworkUtils.isConnectedToNetwork(context);
            Log.d(TAG, "onReceive(), connectedToNetwork : " + connectedToNetwork);
            if (connectedToNetwork) {
                onConnected();
            } else {
                onDisconnected();
            }
        }
    };

    private void onConnected() {
        if (noConnectivityText.getVisibility() == View.VISIBLE) {
            if (honeycombOrGreater) {
                flipFromNoConnToLoginAnimator.start();
            }
        }
    }

    private void onDisconnected() {
        if (honeycombOrGreater) {
            if (loginButton.getVisibility() == View.VISIBLE) {
                flipFromLoginToNoConnAnimator.start();
            } else if (authenticatingText.getVisibility() == View.VISIBLE) {
                flipFromAuthToNoConnAnimator.start();
            }
        }

        if (backgroundAuthenticationTask != null && backgroundAuthenticationTask.getStatus() == AsyncTask.Status.RUNNING) {
            backgroundAuthenticationTask.cancel(true);
        }
    }

    private void initAnimators() {
        flipFromLoginToNoConnAnimator = ValueAnimator.ofFloat(0, 1);
        flipFromNoConnToLoginAnimator = ValueAnimator.ofFloat(0, 1);
        flipFromLoginToAuthAnimator = ValueAnimator.ofFloat(0, 1);
        flipFromAuthToLoginAnimator = ValueAnimator.ofFloat(0, 1);
        flipFromAuthToNoConnAnimator = ValueAnimator.ofFloat(0, 1);

        initFlipAnimation(flipFromLoginToNoConnAnimator, loginButton, noConnectivityText);
        initFlipAnimation(flipFromNoConnToLoginAnimator, noConnectivityText, loginButton);
        initFlipAnimation(flipFromLoginToAuthAnimator, loginButton, authenticatingText);
        initFlipAnimation(flipFromAuthToLoginAnimator, authenticatingText, loginButton);
        initFlipAnimation(flipFromAuthToNoConnAnimator, authenticatingText, noConnectivityText);
    }

    public void initFlipAnimation(ValueAnimator valueAnimator, final View from, final View to) {
        valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        valueAnimator.setDuration(300);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedFraction = animation.getAnimatedFraction();

                if (from.getVisibility() == View.VISIBLE) {
                    if (animatedFraction > 0.5) {
                        from.setVisibility(View.INVISIBLE);
                        to.setVisibility(View.VISIBLE);
                    }
                } else if (to.getVisibility() == View.VISIBLE) {
                    to.setRotationX(-180 * (1 - animatedFraction));
                }

                if (from.getVisibility() == View.VISIBLE) {
                    from.setRotationX(180 * animatedFraction);
                }
            }
        });
    }
}
