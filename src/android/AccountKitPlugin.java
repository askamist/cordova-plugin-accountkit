package org.apache.cordova.facebook;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.facebook.accountkit.AccountKit;
import com.facebook.accountkit.AccessToken;
import com.facebook.accountkit.AccountKitCallback;
import com.facebook.accountkit.AccountKitError;
import com.facebook.accountkit.AccountKitLoginResult;
import com.facebook.accountkit.ui.AccountKitActivity;
import com.facebook.accountkit.ui.AccountKitConfiguration;
import com.facebook.accountkit.ui.LoginType;


public class AccountKitPlugin extends CordovaPlugin {
  private static final String TAG = "AccountKitPlugin";
  public static int APP_REQUEST_CODE = 42;
  private CallbackContext loginContext = null;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    AccountKit.initialize(cordova.getActivity().getApplicationContext());
  }

  @Override
  public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    if ("loginWithPhoneNumber".equals(action)) {
      cordova.getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          executeLogin(LoginType.PHONE, callbackContext);
        }
      });
      return true;

    } else if ("loginWithEmail".equals(action)) {
      cordova.getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          executeLogin(LoginType.EMAIL, callbackContext);
        }
      });
      return true;

    } else if ("getAccessToken".equals(action)) {
      if (hasAccessToken()) {
        callbackContext.success(formatAccessToken(AccountKit.getCurrentAccessToken()));
      } else {
        callbackContext.error("Session not open.");
      }
      return true;

    } else if ("logout".equals(action)) {
      AccountKit.logOut();
      callbackContext.success();
      return true;

    }
    return false;
  }

  public final void executeLogin(LoginType type, CallbackContext callbackContext) {
    // Set a pending callback to cordova
    loginContext = callbackContext;
    PluginResult pr = new PluginResult(PluginResult.Status.NO_RESULT);
    pr.setKeepCallback(true);
    loginContext.sendPluginResult(pr);

    if (hasAccessToken()) {
      //callbackContext.success(formatAccessToken(AccountKit.getCurrentAccessToken()));
      getAccountDetails();
      return;
    }

    Intent intent = new Intent(this.cordova.getActivity(), AccountKitActivity.class);
    AccountKitConfiguration.AccountKitConfigurationBuilder configurationBuilder =
      new AccountKitConfiguration.AccountKitConfigurationBuilder(
          type,
          AccountKitActivity.ResponseType.TOKEN);
    intent.putExtra(AccountKitActivity.ACCOUNT_KIT_ACTIVITY_CONFIGURATION, configurationBuilder.build());

    cordova.setActivityResultCallback(this);
    cordova.startActivityForResult(this, intent, APP_REQUEST_CODE);
  }

  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    // Sometimes intent is null what crashes the app. This is a workaround rather than a solution.
    if (requestCode != APP_REQUEST_CODE || intent == null) {
      return;
    }

    AccountKitLoginResult loginResult = intent.getParcelableExtra(AccountKitLoginResult.RESULT_KEY);
    if (loginResult.getError() != null) {
      loginContext.error(loginResult.getError().getErrorType().getMessage());

    } else if (loginResult.wasCancelled()) {
      loginContext.error("User cancelled dialog");

    } else {
      JSONObject result = null;

      try {
        final AccessToken accessToken = loginResult.getAccessToken();
        if (accessToken != null) {
          getAccountDetails();
        } else {
          result = new JSONObject();
          result.put("code", loginResult.getAuthorizationCode());
          result.put("state", loginResult.getFinalAuthorizationState());
          loginContext.success(result);
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }
    //loginContext = null;
  }

  private boolean hasAccessToken() {
    return AccountKit.getCurrentAccessToken() != null;
  }

  public JSONObject formatAccessToken(AccessToken accessToken) throws JSONException {
    JSONObject result = new JSONObject();
    result.put("accountId", accessToken.getAccountId());
    result.put("applicationId", accessToken.getApplicationId());
    result.put("token", accessToken.getToken());
    result.put("lastRefresh", accessToken.getLastRefresh().getTime());
    result.put("refreshInterval", accessToken.getTokenRefreshIntervalSeconds());
    return result;
  }

  public void getAccountDetails() {
    AccountKit.getCurrentAccount(new AccountKitCallback<com.facebook.accountkit.Account>() {
      @Override
      public void onSuccess(com.facebook.accountkit.Account account) {

        try {
          JSONObject result = new JSONObject();
          AccessToken accessToken = AccountKit.getCurrentAccessToken();
          result.put("accountId", accessToken.getAccountId());
          result.put("applicationId", accessToken.getApplicationId());
          result.put("token", accessToken.getToken());
          result.put("lastRefresh", accessToken.getLastRefresh().getTime());
          result.put("refreshInterval", accessToken.getTokenRefreshIntervalSeconds());

          if (account.getEmail() != null) {
            result.put("email", account.getEmail());
          } else if (account.getPhoneNumber() != null) {
            result.put("mobile", account.getPhoneNumber().toString());
          }

          loginContext.success(result);
        } catch (JSONException e) {
          loginContext.error("unexpected JSON exception");
        }
      }

      @Override
      public void onError(AccountKitError accountKitError) {
        try {
          JSONObject result = new JSONObject();
          AccessToken accessToken = AccountKit.getCurrentAccessToken();
          result.put("accessToken", accessToken.getToken());
          result.put("provider", "accountkit");
          result.put("id", accessToken.getAccountId());
          result.put("error", "unable to get email or phone number");

          loginContext.error(result);
        } catch (JSONException e) {
          loginContext.error("unexpected JSON exception");
        }
      }
    });
  }
}
