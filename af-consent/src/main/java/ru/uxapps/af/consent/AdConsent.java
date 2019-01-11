package ru.uxapps.af.consent;

import com.google.ads.consent.AdProvider;

import java.util.List;

public interface AdConsent {

    interface Callback<T> {

        void onResult(T data);

    }

    void setAskConsentAction(Runnable action);

    void requestProviders(Callback<List<AdProvider>> onResult);

    boolean isConsentWereAsked();

    void setPersonalAd(boolean personal);

    boolean isPersonalAd();

}
