/*
 * ownCloud Android client application
 *
 * @author Bartek Przybylski
 * @author masensio
 * @author David A. Velasco
 * @author Christian Schabesberger
 * @author David González Verdugo
 * @author Abel García de Prada
 * Copyright (C) 2011 Bartek Przybylski
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.owncloud.android.BuildConfig;
import com.owncloud.android.R;
import com.owncloud.android.utils.DocumentProviderUtils;
import com.owncloud.android.utils.PreferenceUtils;
import timber.log.Timber;

import java.util.Arrays;

public class PassCodeActivity extends BaseActivity {

    public final static String ACTION_REQUEST_WITH_RESULT = "ACTION_REQUEST_WITH_RESULT";
    public final static String ACTION_CHECK_WITH_RESULT = "ACTION_CHECK_WITH_RESULT";
    public final static String ACTION_CHECK = "ACTION_CHECK";

    public final static String KEY_PASSCODE = "KEY_PASSCODE";
    public final static String KEY_CHECK_RESULT = "KEY_CHECK_RESULT";

    // NOTE: PREFERENCE_SET_PASSCODE must have the same value as settings_security.xml-->android:key for passcode preference
    public final static String PREFERENCE_SET_PASSCODE = "set_pincode";

    public final static String PREFERENCE_PASSCODE = "PrefPinCode";
    public final static String PREFERENCE_PASSCODE_D = "PrefPinCode";
    public final static String PREFERENCE_PASSCODE_D1 = "PrefPinCode1";
    public final static String PREFERENCE_PASSCODE_D2 = "PrefPinCode2";
    public final static String PREFERENCE_PASSCODE_D3 = "PrefPinCode3";
    public final static String PREFERENCE_PASSCODE_D4 = "PrefPinCode4";

    private Button mBCancel;
    private TextView mPassCodeHdr;
    private TextView mPassCodeHdrExplanation;
    private TextView mPassCodeError;
    public final static int numberOfPassInputs = 4;
    private EditText[] mPassCodeEditTexts = new EditText[numberOfPassInputs];

    private String[] mPassCodeDigits = new String[numberOfPassInputs];
    private static String KEY_PASSCODE_DIGITS = "PASSCODE_DIGITS";
    private boolean mConfirmingPassCode = false;
    private static String KEY_CONFIRMING_PASSCODE = "CONFIRMING_PASSCODE";

    private boolean mBChange = true; // to control that only one blocks jump

    /**
     * Initializes the activity.
     *
     * @param savedInstanceState    Previously saved state - irrelevant in this case
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /// protection against screen recording
        if (!BuildConfig.DEBUG) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } // else, let it go, or taking screenshots & testing will not be possible

        setContentView(R.layout.passcodelock);

        // Allow or disallow touches with other visible windows
        LinearLayout passcodeLockLayout = findViewById(R.id.passcodeLockLayout);
        passcodeLockLayout.setFilterTouchesWhenObscured(
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)
        );

        mBCancel = findViewById(R.id.cancel);
        mPassCodeHdr = findViewById(R.id.header);
        mPassCodeHdrExplanation = findViewById(R.id.explanation);
        mPassCodeHdrExplanation.setFilterTouchesWhenObscured(
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)
        );
        mPassCodeError = findViewById(R.id.error);

        inflatePasscodeTxtLine(passcodeLockLayout);

        if (ACTION_CHECK.equals(getIntent().getAction())) {
            /// this is a pass code request; the user has to input the right value
            mPassCodeHdr.setText(R.string.pass_code_enter_pass_code);
            mPassCodeHdrExplanation.setVisibility(View.INVISIBLE);
            setCancelButtonEnabled(false);      // no option to cancel

        } else if (ACTION_REQUEST_WITH_RESULT.equals(getIntent().getAction())) {
            if (savedInstanceState != null) {
                mConfirmingPassCode = savedInstanceState.getBoolean(PassCodeActivity.KEY_CONFIRMING_PASSCODE);
                mPassCodeDigits = savedInstanceState.getStringArray(PassCodeActivity.KEY_PASSCODE_DIGITS);
            }
            if (mConfirmingPassCode) {
                //the app was in the passcodeconfirmation
                requestPassCodeConfirmation();
            } else {
                /// pass code preference has just been activated in Preferences;
                // will receive and confirm pass code value
                mPassCodeHdr.setText(R.string.pass_code_configure_your_pass_code);
                //mPassCodeHdr.setText(R.string.pass_code_enter_pass_code);
                // TODO choose a header, check iOS
                mPassCodeHdrExplanation.setVisibility(View.VISIBLE);
                setCancelButtonEnabled(true);
            }

        } else if (ACTION_CHECK_WITH_RESULT.equals(getIntent().getAction())) {
            /// pass code preference has just been disabled in Preferences;
            // will confirm user knows pass code, then remove it
            mPassCodeHdr.setText(R.string.pass_code_remove_your_pass_code);
            mPassCodeHdrExplanation.setVisibility(View.INVISIBLE);
            setCancelButtonEnabled(true);

        } else {
            throw new IllegalArgumentException(R.string.illegal_argument_exception_message + " ");
        }

        setTextListeners();
    }

    private void inflatePasscodeTxtLine(LinearLayout passcodeLockLayout) {
        final LinearLayout passcodeTxtLayout = findViewById(R.id.passCodeTxtLayout);
        for(int i = 0; i < numberOfPassInputs; i++) {
            EditText txt = (EditText) getLayoutInflater().inflate(R.layout.passcode_edit_text, passcodeTxtLayout, false);
            passcodeTxtLayout.addView(txt);
            mPassCodeEditTexts[i] = txt;
        }
        mPassCodeEditTexts[0].requestFocus();
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }


    /**
     * Enables or disables the cancel button to allow the user interrupt the ACTION
     * requested to the activity.
     *
     * @param enabled       'True' makes the cancel button available, 'false' hides it.
     */
    protected void setCancelButtonEnabled(boolean enabled) {
        if (enabled) {
            mBCancel.setVisibility(View.VISIBLE);
            mBCancel.setOnClickListener(v -> finish());
        } else {
            mBCancel.setVisibility(View.GONE);
            mBCancel.setVisibility(View.INVISIBLE);
            mBCancel.setOnClickListener(null);
        }
    }

    /**
     * Binds the appropiate listeners to the input boxes receiving each digit of the pass code.
     */
    protected void setTextListeners() {
        for (int i = 0; i < numberOfPassInputs; i++) {
            mPassCodeEditTexts[i].addTextChangedListener(new PassCodeDigitTextWatcher(i, i == numberOfPassInputs - 1));

            final int index = i; //make i final because it is used as a caputer
            if (i > 0) {
                mPassCodeEditTexts[i].setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DEL && mBChange) {  // TODO WIP: event should be
                        // used to control what's exactly happening with DEL, not any custom field...
                        mPassCodeEditTexts[index - 1].setText("");
                        mPassCodeEditTexts[index - 1].requestFocus();
                        if (!mConfirmingPassCode) {
                            mPassCodeDigits[index - 1] = "";
                        }
                        mBChange = false;
                    } else if (!mBChange) {
                        mBChange = true;
                    }
                    return false;
                });
            }

            mPassCodeEditTexts[i].setOnFocusChangeListener((v, hasFocus) -> {
                /// TODO WIP: should take advantage of hasFocus to reduce processing
                for (int j = 0; j < index; j++) {
                    if (mPassCodeEditTexts[j].getText().toString().equals("")) {  // TODO WIP validation
                        // could be done in a global way, with a single OnFocusChangeListener for all the
                        // input fields
                        mPassCodeEditTexts[j].requestFocus();
                        break;
                    }
                }
            });
        }
    }

    /**
     * Processes the pass code entered by the user just after the last digit was in.
     *
     * Takes into account the action requested to the activity, the currently saved pass code and
     * the previously typed pass code, if any.
     */
    private void processFullPassCode() {
        if (ACTION_CHECK.equals(getIntent().getAction())) {
            if (checkPassCodeIsValid()) {
                /// pass code accepted in request, user is allowed to access the app
                mPassCodeError.setVisibility(View.INVISIBLE);
                hideSoftKeyboard();
                finish();

            } else {
                showErrorAndRestart(R.string.pass_code_wrong, R.string.pass_code_enter_pass_code,
                        View.INVISIBLE);
            }

        } else if (ACTION_CHECK_WITH_RESULT.equals(getIntent().getAction())) {
            if (checkPassCodeIsValid()) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(KEY_CHECK_RESULT, true);
                setResult(RESULT_OK, resultIntent);
                mPassCodeError.setVisibility(View.INVISIBLE);
                hideSoftKeyboard();
                DocumentProviderUtils.Companion.notifyDocumentProviderRoots(getApplicationContext());
                finish();
            } else {
                showErrorAndRestart(R.string.pass_code_wrong, R.string.pass_code_enter_pass_code,
                        View.INVISIBLE);
            }

        } else if (ACTION_REQUEST_WITH_RESULT.equals(getIntent().getAction())) {
            /// enabling pass code
            if (!mConfirmingPassCode) {
                mPassCodeError.setVisibility(View.INVISIBLE);
                requestPassCodeConfirmation();
            } else if (confirmPassCode()) {
                /// confirmed: user typed the same pass code twice
                savePassCodeAndExit();

            } else {
                showErrorAndRestart(
                        R.string.pass_code_mismatch, R.string.pass_code_configure_your_pass_code, View.VISIBLE
                );
            }
        }
    }

    private void showErrorAndRestart(int errorMessage, int headerMessage,
                                     int explanationVisibility) {
        Arrays.fill(mPassCodeDigits, null);
        mPassCodeError.setText(errorMessage);
        mPassCodeError.setVisibility(View.VISIBLE);
        mPassCodeHdr.setText(headerMessage);                // TODO check if really needed
        mPassCodeHdrExplanation.setVisibility(explanationVisibility); // TODO check if really needed
        clearBoxes();
    }

    /**
     * Ask to the user for retyping the pass code just entered before saving it as the current pass
     * code.
     */
    protected void requestPassCodeConfirmation() {
        clearBoxes();
        mPassCodeHdr.setText(R.string.pass_code_reenter_your_pass_code);
        mPassCodeHdrExplanation.setVisibility(View.INVISIBLE);
        mConfirmingPassCode = true;
    }

    /**
     * Compares pass code entered by the user with the value currently saved in the app.
     *
     * @return     'True' if entered pass code equals to the saved one.
     */
    protected boolean checkPassCodeIsValid() {
        final SharedPreferences appPrefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        final String passcodeString = appPrefs.getString(PREFERENCE_PASSCODE, loadPinFromOldFormatIfPossible());

        boolean isValid = true;
        for (int i = 0; i < mPassCodeDigits.length && isValid; i++) {
            String originalDigit = Character.toString(passcodeString.charAt(i));
            isValid = (mPassCodeDigits[i] != null) &&
                    mPassCodeDigits[i].equals(originalDigit);
        }
        return isValid;
    }

    /**
     * Compares pass code retyped by the user in the input fields with the value entered just
     * before.
     *
     * @return     'True' if retyped pass code equals to the entered before.
     */
    protected boolean confirmPassCode() {
        mConfirmingPassCode = false;

        boolean isValid = true;
        for (int i = 0; i < mPassCodeEditTexts.length && isValid; i++) {
            isValid = ((mPassCodeEditTexts[i].getText().toString()).equals(mPassCodeDigits[i]));
        }
        return isValid;
    }

    /**
     * Sets the input fields to empty strings and puts the focus on the first one.
     */
    protected void clearBoxes() {
        for (EditText passCodeEditText : mPassCodeEditTexts) {
            passCodeEditText.setText("");
        }
        mPassCodeEditTexts[0].requestFocus();
    }

    /**
     * Overrides click on the BACK arrow to correctly cancel ACTION_ENABLE or ACTION_DISABLE, while
     * preventing than ACTION_CHECK may be worked around.
     *
     * @param keyCode       Key code of the key that triggered the down event.
     * @param event         Event triggered.
     * @return              'True' when the key event was processed by this method.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (ACTION_REQUEST_WITH_RESULT.equals(getIntent().getAction()) ||
                    ACTION_CHECK_WITH_RESULT.equals(getIntent().getAction())) {
                finish();
            }   // else, do nothing, but report that the key was consumed to stay alive
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Saves the pass code input by the user as the current pass code.
     */
    protected void savePassCodeAndExit() {
        Intent resultIntent = new Intent();

        StringBuilder passCodeString = new StringBuilder();
        for(int i = 0; i < numberOfPassInputs; i++) {
            passCodeString.append(mPassCodeDigits[i]);
        }
        resultIntent.putExtra(KEY_PASSCODE, passCodeString.toString());

        setResult(RESULT_OK, resultIntent);
        DocumentProviderUtils.Companion.notifyDocumentProviderRoots(getApplicationContext());
        finish();
    }

    private String loadPinFromOldFormatIfPossible() {
        SharedPreferences appPrefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        StringBuilder pinString = new StringBuilder();
        pinString.append(appPrefs.getString(PREFERENCE_PASSCODE_D1, null));
        pinString.append(appPrefs.getString(PREFERENCE_PASSCODE_D2, null));
        pinString.append(appPrefs.getString(PREFERENCE_PASSCODE_D3, null));
        pinString.append(appPrefs.getString(PREFERENCE_PASSCODE_D4, null));
        return pinString.toString();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PassCodeActivity.KEY_CONFIRMING_PASSCODE, mConfirmingPassCode);
        outState.putStringArray(PassCodeActivity.KEY_PASSCODE_DIGITS, mPassCodeDigits);
    }

    private class PassCodeDigitTextWatcher implements TextWatcher {

        private int mIndex;
        private boolean mLastOne;

        /**
         * Constructor
         *
         * @param index         Position in the pass code of the input field that will be bound to
         *                      this watcher.
         * @param lastOne       'True' means that watcher corresponds to the last position of the
         *                      pass code.
         */
        public PassCodeDigitTextWatcher(int index, boolean lastOne) {
            mIndex = index;
            mLastOne = lastOne;
            if (mIndex < 0) {
                throw new IllegalArgumentException(
                        "Invalid index in " + PassCodeDigitTextWatcher.class.getSimpleName() +
                                " constructor"
                );
            }
        }

        private int next() {
            return mLastOne ? 0 : mIndex + 1;
        }

        /**
         * Performs several actions when the user types a digit in an input field:
         *  - saves the input digit to the state of the activity; this will allow retyping the
         *    pass code to confirm it.
         *  - moves the focus automatically to the next field
         *  - for the last field, triggers the processing of the full pass code
         *
         * @param s     Changed text
         */
        @Override
        public void afterTextChanged(Editable s) {
            if (s.length() > 0) {
                if (!mConfirmingPassCode) {
                    mPassCodeDigits[mIndex] = mPassCodeEditTexts[mIndex].getText().toString();
                }
                mPassCodeEditTexts[next()].requestFocus();

                if (mLastOne) {
                    processFullPassCode();
                }

            } else {
                Timber.d("Text box " + mIndex + " was cleaned");
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // nothing to do
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // nothing to do
        }
    }
}