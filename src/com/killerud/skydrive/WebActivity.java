package com.killerud.skydrive;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.killerud.skydrive.util.WebViewFixed;

/**
 * In large part taken from the Reddit is fun/Diode open source project available here:
 *  https://github.com/zagaberoo/diode/
 */
public class WebActivity extends SherlockActivity {
    public static final String EXTRA_FILE_LINK = "extra_file_link";

    private String mTitle;
    private WebViewFixed mWebView;
    private String mUri;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.web_activity);

        mUri = getIntent().getStringExtra(EXTRA_FILE_LINK);

        mWebView = (WebViewFixed) findViewById(R.id.webBrowserView);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setPluginsEnabled(true);
        mWebView.getSettings().setUseWideViewPort(true);

        mWebView.setBackgroundColor(0);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String host = Uri.parse(url).getHost();
                if (host != null && mTitle != null) {
                    setTitle(host + " : " + mTitle);
                } else if (host != null) {
                    setTitle(host);
                } else if (mTitle != null) {
                    setTitle(mTitle);
                }
            }
        });

        final Activity activity = this;

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                // Activities and WebViews measure progress with different scales.
                // The progress meter will automatically disappear when we reach 100%
                activity.setProgress(progress * 100);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                mTitle = title;
                setTitle(title);
            }
        }
        );

        if (savedInstanceState != null) {
            mWebView.restoreState(savedInstanceState);
        } else {
            mWebView.loadUrl(mUri);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.web_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                return true;
            case R.id.openBrowser:
                if (mUri == null)
                    return true;
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUri));
                startActivity(browserIntent);
                return true;
            case R.id.close:
                finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Must remove the WebView from the view system before destroying.
        mWebView.setVisibility(View.GONE);
        mWebView.destroy();
        mWebView = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mWebView.saveState(outState);
    }

}
