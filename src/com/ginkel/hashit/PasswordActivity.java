/*
 * This file is part of Hash It!.
 * 
 * Copyright (C) 2009-2011 Thilo-Alexander Ginkel.
 * Copyright (C) 2011-2013 TG Byte Software GmbH.
 * 
 * Hash It! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Hash It! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Hash It!.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ginkel.hashit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.widget.TextView.BufferType;
import android.widget.TextView.OnEditorActionListener;
import com.ginkel.hashit.Constants.*;
import com.ginkel.hashit.util.*;
import com.ginkel.hashit.util.cache.MemoryCacheService;
import com.ginkel.hashit.util.cache.MemoryCacheServiceImpl;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ginkel.hashit.Constants.*;

public class PasswordActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private EditText siteTag;
    private AutoCompleteTextView autoCompleteSiteTag;
    private boolean ignoreTagChange;

    private EditText masterKey;
    private TextView masterKeyOverlay;
    private EditText hashWord;
    private Button hashPassword;

    private CharSequence originalHost;

    private boolean restoredState;
    private boolean restoredSiteTag;
    private boolean welcomeDisplayed;

    private FocusRequest focus = FocusRequest.NONE;

    private SharedPreferences settings;

    /**
     * A pattern used to extract a site tag from a host name
     */
    private static final Pattern SITE_PATTERN = Pattern
            .compile("^.*?([\\w\\d\\-]+)\\.((co|com|net|org|ac)\\.)?\\w+$");

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Typeface monospacedTypeface = Typeface.createFromAsset(getAssets(), "fonts/SourceCodePro-Regular.ttf");

        settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        settings.registerOnSharedPreferenceChangeListener(this);

        setContentView(R.layout.main);

        siteTag = (EditText) findViewById(R.id.SiteTag);
        siteTag.addTextChangedListener(new TextWatcher() {

            public void onTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
                synchronized (PasswordActivity.this) {
                    if (!ignoreTagChange) {
                        publishSiteTag(PasswordActivity.this, s.toString());
                    }
                }
            }
        });

        autoCompleteSiteTag = (AutoCompleteTextView) siteTag;

        masterKeyOverlay = (TextView) findViewById(R.id.MasterKeyOverlay);

        masterKey = (EditText) findViewById(R.id.MasterKey);
        masterKey.setOnEditorActionListener(new OnEditorActionListener() {
            private long lastProcessedEvent;

            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || actionId == EditorInfo.IME_ACTION_NEXT) {
                    /*
                     * the type and number of events depends on the soft keyboard in use, so
                     * debounce the event and make sure that only the first one triggers an action
                     */
                    if (System.currentTimeMillis() - lastProcessedEvent > 1000) {
                        hashPassword();
                        lastProcessedEvent = System.currentTimeMillis();
                    }
                    return true;
                }
                return false;
            }
        });
        if (masterKeyOverlay != null) {
            masterKey.addTextChangedListener(new TextWatcherAdapter() {
                @Override
                public void afterTextChanged(Editable s) {
                    showMasterKeyDigest(masterKey.getText());
                }
            });
            masterKeyOverlay.setTypeface(monospacedTypeface);
        }

        hashWord = (EditText) findViewById(R.id.HashWord);
        hashWord.setEnabled(false);
        hashWord.setTypeface(monospacedTypeface);

        hashPassword = (Button) findViewById(R.id.Calculate);
        hashPassword.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                hashPassword();
            }
        });

        findViewById(R.id.Bump).setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                String tag = siteTag.getText().toString();
                try {
                    CharSequence newSiteTag = PasswordHasher.bumpSiteTag(tag);
                    siteTag.setText(newSiteTag);
                    publishSiteTag(v.getContext(), newSiteTag.toString());
                } catch (NumberFormatException e) {
                    Toast.makeText(getBaseContext(), R.string.Message_InvalidSiteTag,
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        originalHost = null;

        focus = FocusRequest.SITE_TAG;

        Intent intent = getIntent();
        Log.d(LOG_TAG, "intent = " + intent);
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            // we have been called via the "Send to" intent

            if (!restoredSiteTag) {
                /* we did not just switch back and forth between the password and the parameters tab */

                String uriCandidate = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.d(LOG_TAG, "uriCandidate = " + uriCandidate);
                if (uriCandidate != null) {
                    try {
                        Uri uri = Uri.parse(uriCandidate);
                        String host = uri.getHost();
                        originalHost = host;

                        String site = settings.getString(String.format(SITE_MAP, host), null);

                        if (site != null) {
                            siteTag.setText(site);
                            publishSiteTag(this, site);
                            focus = FocusRequest.MASTER_KEY;
                        } else {
                            Log.d(LOG_TAG, "host = " + host);
                            Matcher siteExtractor = SITE_PATTERN.matcher(host);
                            if (siteExtractor.matches()) {
                                site = siteExtractor.group(1);
                                siteTag.setText(site);
                                publishSiteTag(this, site);
                                focus = FocusRequest.MASTER_KEY;
                            } else {
                                Toast.makeText(getBaseContext(), R.string.Message_SiteTagFailure,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to retrieve intent URI", e);
                        Toast.makeText(getBaseContext(), R.string.Message_SiteTagFailure,
                                Toast.LENGTH_LONG).show();
                    }
                }
            }

            findViewById(R.id.UsageInformation).setVisibility(View.GONE);
        } else {
            findViewById(R.id.UsageInformation).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_WELCOME_DISPLAYED, welcomeDisplayed);
        outState.putCharSequence(STATE_SITE_TAG, siteTag.getText());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        welcomeDisplayed = savedInstanceState.getBoolean(STATE_WELCOME_DISPLAYED, welcomeDisplayed);

        CharSequence savedSiteTag = savedInstanceState.getCharSequence(STATE_SITE_TAG);
        if (savedSiteTag != null) {
            siteTag.setText(savedSiteTag);
            restoredSiteTag = true;
        } else {
            restoredSiteTag = false;
        }

        restoredState = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!restoredState) {
            /* do not mess with input focus if we restored the state */

            /* focus changes do not seem to work in onCreate */
            switch (focus) {
                case SITE_TAG:
                    siteTag.requestFocus();
                    autoCompleteSiteTag.dismissDropDown();
                    break;
                case MASTER_KEY:
                    masterKey.requestFocus();
                    break;
            }
        }

        // Site Tag history
        autoCompleteSiteTag.setThreshold(1);
        HistoryManager siteTagHistory;
        if ((siteTagHistory = HashItApplication.getApp(this).getHistoryManager()) == null) {
            HashItApplication.getApp(this).setHistoryManager(
                    siteTagHistory = new HistoryManager(this, SITE_TAGS, R.layout.autocomplete_list));
        }
        if (getBool(ENABLE_HISTORY, settings, null, true))
            autoCompleteSiteTag.setAdapter(siteTagHistory.getAdapter());
        else
            autoCompleteSiteTag.setAdapter(new NullAdapter<String>(this, R.layout.autocomplete_list));

        if (getStringAsInt(CACHE_DURATION, settings, null, -1) > 0) {
            final Context ctx = getApplicationContext();
            ctx.bindService(new Intent(ctx, MemoryCacheServiceImpl.class), new ServiceConnection() {

                public void onServiceDisconnected(ComponentName name) {
                }

                public void onServiceConnected(ComponentName name, IBinder service) {
                    final String cachedKey = ((MemoryCacheService.Binder) service).getService()
                            .getEntry(MASTER_KEY_CACHE);

                    if (cachedKey != null)
                        masterKey.setText(cachedKey);
                    showMasterKeyDigest(cachedKey);
                }
            }, BIND_AUTO_CREATE);
        }

        displayWelcomeScreen();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // back up history
        HashItApplication.getApp(this).getHistoryManager().save();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (settings != null)
            settings.unregisterOnSharedPreferenceChangeListener(this);
    }

    private static boolean getBool(String key, SharedPreferences prefs, SharedPreferences defaults,
                                   boolean def) {
        return prefs.getBoolean(key, defaults != null ? defaults.getBoolean(key, def) : def);
    }

    /**
     * Works around issue 2096.
     */
    private static int getStringAsInt(String key, SharedPreferences prefs,
                                      SharedPreferences defaults, int def) {
        return Integer.valueOf(prefs.getString(key, defaults != null ? defaults.getString(key,
                String.valueOf(def)) : String.valueOf(def)));
    }

    /**
     * A convenience method to forward the current site tag to the application.
     */
    private static void publishSiteTag(Context ctx, String siteTag) {
        HashItApplication.getApp(ctx).setSiteTag(siteTag);
    }

    private void displayWelcomeScreen() {
        if (!welcomeDisplayed) {
            final Integer hideUpToVersion = settings.getInt(HIDE_WELCOME_SCREEN, 0);
            final PackageInfo packageInfo = HashItApplication.getApp(this).getPackageInfo();
            final int versionCode = packageInfo == null ? Integer.MAX_VALUE : packageInfo.versionCode;

            if (hideUpToVersion == null || packageInfo == null || hideUpToVersion < packageInfo.versionCode) {
                View view = View.inflate(PasswordActivity.this, R.layout.welcome, null);
                TextView textView = (TextView) view.findViewById(R.id.message);
                textView.setMovementMethod(LinkMovementMethod.getInstance());
                textView.setText(R.string.Text_Welcome);
                final CheckBox dontShowAgain = (CheckBox) view.findViewById(R.id.CheckBox_DoNotShowAgain);
                /*
                 * we can't reliably store the current version if we could not determine the package
                 * version code
                 */
                dontShowAgain.setEnabled(packageInfo != null);
                final WelcomeDialogListener welcomeListener = new WelcomeDialogListener(dontShowAgain, versionCode);
                new AlertDialog.Builder(PasswordActivity.this).setTitle(R.string.Title_Welcome)
                        .setView(view).setPositiveButton(android.R.string.ok, welcomeListener)
                        .setOnCancelListener(welcomeListener).setIcon(R.drawable.icon).show();
                welcomeDisplayed = true;
            }
        }
    }

    private class WelcomeDialogListener implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener {
        private final CheckBox dontShowAgain;
        private final int versionCode;

        WelcomeDialogListener(CheckBox dontShowAgain, int versionCode) {
            this.dontShowAgain = dontShowAgain;
            this.versionCode = versionCode;
        }

        public void onCancel(DialogInterface dialog) {
            onDismissDialog();
        }

        public void onClick(DialogInterface dialog, int which) {
            onDismissDialog();
        }

        private void onDismissDialog() {
            if (dontShowAgain.isChecked()) {
                settings.edit().putInt(HIDE_WELCOME_SCREEN, versionCode).commit();
            }
        }
    }

    private void hashPassword() {
        final String originalTag = siteTag.getText().toString();
        final String key = masterKey.getText().toString();
        String tag = originalTag;

        if (tag.length() == 0) {
            Toast.makeText(getBaseContext(), R.string.Message_SiteTagEmpty, Toast.LENGTH_LONG).show();
            siteTag.requestFocus();
        } else if (key.length() == 0) {
            Toast.makeText(getBaseContext(), R.string.Message_MasterKeyEmpty, Toast.LENGTH_LONG).show();
            masterKey.requestFocus();
        } else {
            boolean compatibility = tag.startsWith(COMPATIBILITY_PREFIX);
            if (compatibility) {
                tag = tag.substring(COMPATIBILITY_PREFIX.length());
            }

            SharedPreferences prefs = getSharedPreferences(HashItApplication.normalizeSiteTag(tag), MODE_PRIVATE);
            if (!compatibility) {
                compatibility = prefs.getBoolean(COMPATIBILITY_MODE,
                        prefs.getInt(APP_VERSION, -1) < 19 && prefs.getAll().size() > 0)
                        || settings.getBoolean(COMPATIBILITY_MODE, true);
            }

            Log.i(LOG_TAG, String.format("Compatibility mode %s", compatibility ? "enabled" : "disabled"));

            if (!compatibility) {
                tag = PasswordHasher.hashPassword(SeedHelper.getSeed(settings), tag, //
                        24, //
                        true, // require digits
                        true, // require punctuation
                        true, // require mixed case
                        false, // no special chars
                        false // only digits
                );
            }

            String hash = PasswordHasher.hashPassword(tag, key, //
                    getStringAsInt(HASH_WORD_SIZE, prefs, settings, 8), //
                    getBool(REQUIRE_DIGITS, prefs, settings, true), //
                    getBool(REQUIRE_PUNCTUATION, prefs, settings, true), //
                    getBool(REQUIRE_MIXED_CASE, prefs, settings, true), //
                    getBool(RESTRICT_SPECIAL_CHARS, prefs, settings, false), //
                    getBool(RESTRICT_DIGITS, prefs, settings, false));

            hashWord.setText(hash, BufferType.NORMAL);

            boolean copiedToClipboard = false;
            if (!settings.getBoolean(DISABLE_CLIPBOARD, false))
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setText(hash);
                    copiedToClipboard = true;
                    Toast.makeText(getBaseContext(), R.string.Message_HashCopiedToClipboard, Toast.LENGTH_LONG).show();
                } catch (NullPointerException e) {
                    // catches Samsung clipboard bug -- cf. http://veetipaananen.fi/dear-samsung-please-fix-your-clipboard/
                    Toast.makeText(getBaseContext(), R.string.Message_ClipboardError, Toast.LENGTH_LONG).show();
                }

            if (originalHost != null) {
                // save site tag for host name
                settings.edit().putString(String.format(SITE_MAP, originalHost), originalTag).commit();
            }

            if (settings.getBoolean(ENABLE_HISTORY, true)) {
                HashItApplication.getApp(this).getHistoryManager().add(originalTag);
                masterKey.requestFocus();
            }

            final int cacheDuration = getStringAsInt(CACHE_DURATION, settings, null, -1);
            if (cacheDuration > 0) {
                final Context ctx = getApplicationContext();
                MemoryCacheServiceImpl.ensureStarted(ctx);
                ctx.bindService(new Intent(ctx, MemoryCacheServiceImpl.class),
                        new ServiceConnection() {

                            public void onServiceDisconnected(ComponentName name) {
                            }

                            public void onServiceConnected(ComponentName name, IBinder service) {
                                ((MemoryCacheService.Binder) service).getService().putEntry(
                                        MASTER_KEY_CACHE, key, cacheDuration * 60 * 1000);
                            }
                        }, 0);
            }

            Intent intent = getIntent();
            if (settings.getBoolean(AUTO_EXIT, false) && intent != null && Intent.ACTION_SEND.equals(intent.getAction()) && copiedToClipboard)
                finish();
        }
    }

    private void showMasterKeyDigest(CharSequence key) {
        if (masterKeyOverlay != null)
            if (key != null && key.length() > 0 && settings.getBoolean(SHOW_MASTER_KEY_DIGEST, true)) {
                try {
                    final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                    masterKeyOverlay.setText(Base64.toBase64(sha1.digest(key.toString().getBytes("UTF-8"))).subSequence(0, 2));
                } catch (NoSuchAlgorithmException e) {
                } catch (UnsupportedEncodingException e) {
                }
            } else
                masterKeyOverlay.setText("");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SHOW_MASTER_KEY_DIGEST.equals(key))
            showMasterKeyDigest(masterKey.getText());
    }
}
