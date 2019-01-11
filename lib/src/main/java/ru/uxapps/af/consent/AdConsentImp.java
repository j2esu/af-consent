package ru.uxapps.af.consent;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.ads.consent.AdProvider;
import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.consent.DebugGeography;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdConsentImp implements AdConsent {

    private interface UpdateListener {

        void onSuccess();

        void onFail();

    }

    private static final String PREF_CONSENT_ASKED = "ru.uxapps.af_consent.CONSENT_ASKED";

    private final String[] mPubIds;

    private final ConsentInformation mInfo;
    private final SharedPreferences mPrefs;
    private final Set<UpdateListener> mListeners = new HashSet<>();

    private Runnable mOnAskConsent;
    private boolean mUpdated;
    private boolean mUpdating;
    private boolean mPendingAsk;

    private AdConsentImp(Context context, String publisherId, boolean debug, boolean debugEea, boolean debugResetConsent) {
        mInfo = ConsentInformation.getInstance(context);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPubIds = new String[]{publisherId};
        //set debug params (will have ne effect in production)
        if (debug) debug(debugEea, debugResetConsent);

        withUpdatedInfo(new UpdateListener() {
            @Override
            public void onSuccess() {
                if (mInfo.isRequestLocationInEeaOrUnknown()) {
                    if (mInfo.getConsentStatus() == ConsentStatus.UNKNOWN) {
                        askConsent();
                    }
                } else {
                    mInfo.setConsentStatus(ConsentStatus.PERSONALIZED);
                }
            }

            @Override
            public void onFail() {}
        });
    }

    private void askConsent() {
        if (mOnAskConsent != null) {
            mOnAskConsent.run();
            mPendingAsk = false;
            mPrefs.edit().putBoolean(PREF_CONSENT_ASKED, true).apply();
        } else mPendingAsk = true;
    }

    public AdConsentImp(Context context, String publisherId, boolean debugEea, boolean debugResetConsent) {
        this(context, publisherId, true, debugEea, debugResetConsent);
    }

    public AdConsentImp(Context context, String publisherId) {
        this(context, publisherId, false, false, false);
    }

    @Override
    public void setAskConsentAction(Runnable action) {
        mOnAskConsent = action;
        if (mPendingAsk) askConsent();
    }

    private void debug(boolean eea, boolean resetConsent) {
        if (resetConsent) mInfo.setConsentStatus(ConsentStatus.UNKNOWN);

        mInfo.addTestDevice(mInfo.getHashedDeviceId());
        mInfo.setDebugGeography(eea ? DebugGeography.DEBUG_GEOGRAPHY_EEA :
                DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA);
    }

    private void withUpdatedInfo(UpdateListener listener) {
        if (listener != null) mListeners.add(listener);

        if (mUpdating) return;

        if (mUpdated) {
            notifyUpdateSuccess();
            return;
        }

        mInfo.requestConsentInfoUpdate(mPubIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                mUpdated = true;
                mUpdating = false;
                notifyUpdateSuccess();
            }

            @Override
            public void onFailedToUpdateConsentInfo(String errorDescription) {
                mUpdating = false;
                notifyUpdateFail();
            }
        });
    }

    private void notifyUpdateSuccess() {
        for (UpdateListener listener : mListeners) listener.onSuccess();
        mListeners.clear();
    }

    private void notifyUpdateFail() {
        for (UpdateListener listener : mListeners) listener.onFail();
        mListeners.clear();
    }

    @Override
    public void setPersonalAd(boolean personal) {
        mInfo.setConsentStatus(personal ? ConsentStatus.PERSONALIZED : ConsentStatus.NON_PERSONALIZED);
    }

    @Override
    public void requestProviders(Callback<List<AdProvider>> callback) {
        withUpdatedInfo(new UpdateListener() {
            @Override
            public void onSuccess() {
                callback.onResult(mInfo.getAdProviders());
            }

            @Override
            public void onFail() {
                callback.onResult(Collections.emptyList());
            }
        });
    }

    @Override
    public boolean isConsentWereAsked() {
        return mPrefs.getBoolean(PREF_CONSENT_ASKED, false);
    }

    @Override
    public boolean isPersonalAd() {
        return mInfo.getConsentStatus() == ConsentStatus.PERSONALIZED;
    }
}
