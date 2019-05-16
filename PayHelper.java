package com.tencent.imsdk.samples;

import android.app.Activity;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;

public class PayHelper implements
        IabHelper.OnIabPurchaseListener, ConsumeResponseListener {
    public static final String TAG = "PayHelper";
    private IabHelper mIabHelper = null;


    //purchase update callback
    @Override
    public void onPurchaseResponse(BillingResult result, List<Purchase> purchasesList) {
        Log.d(TAG, "onPurchaseResponse: " + result.getResponseCode() + ",debugMsg: " + result.getDebugMessage());

        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchasesList != null) {
            for(Purchase purchase : purchasesList){
                handlePurchase(purchase);
            }
        }
    }

    //consume callback
    @Override
    public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
        Log.d(TAG, "onConsumeResponse: " + billingResult.getResponseCode() + ",debugMsg: " + billingResult.getDebugMessage());
    }

    //init IabHelper
    public void init(Activity activity) {
        mIabHelper = new IabHelper(activity);
        mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(BillingResult result) {
                Log.d(TAG, "onIabSetupFinished: " + result.getResponseCode() + ",debugMsg: " + result.getDebugMessage());

                if(result.getResponseCode() == BillingClient.BillingResponseCode.OK){
                    //query purchases,in-app and subs
                    queryPurchases();
                }
            }
        });
    }

    //query purchases,in-app and subs
    public void queryPurchases() {
        mIabHelper.queryPurchasesAsync(new IabHelper.OnIabQueryPurchasesListener() {
            @Override
            public void onQueryPurchasesResponse(BillingResult result, List<Purchase> purchasesList) {
                Log.d(TAG, "onQueryPurchasesResponse: " + result.getResponseCode() + ",debugMsg: " + result.getDebugMessage());

                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && purchasesList != null) {
                    for(Purchase purchase : purchasesList){
                        handlePurchase(purchase);
                    }
                }
            }
        });
    }

    //query product sku details before purchase
    public void querySkuDetails(String productId, @BillingClient.SkuType String type, SkuDetailsResponseListener listener) {
        ArrayList<String> skuList = new ArrayList<String>();
        skuList.add(productId);

        SkuDetailsParams skuDetailsParams = SkuDetailsParams.newBuilder()
                .setSkusList(skuList)
                .setType(type)
                .build();

        mIabHelper.querySkuDetailsAsync(skuDetailsParams, listener);
    }


    //purchase
    public void pay(final Activity activity, final String productId, String type) {
        //query sku details first
        querySkuDetails(productId, type, new SkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                Log.d(TAG, "onSkuDetailsResponse: " + billingResult.getResponseCode() + ",debugMsg: " + billingResult.getDebugMessage());

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && skuDetailsList != null) {
                    for(SkuDetails skuDetails : skuDetailsList){
                        if (productId.equals(skuDetails.getSku())) {
                            Log.d(TAG, "SkuDetails: " + skuDetails.toString());

                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setSkuDetails(skuDetails)
                                    .setAccountId("yours account id")
                                    .setDeveloperId("yours developer id")
                                    .build();
                            //purchase
                            mIabHelper.launchPurchaseFlow(activity, billingFlowParams, PayHelper.this);
                        }
                    }

                }
            }
        });
    }


    //handle purchase result
    private void handlePurchase(final Purchase purchase){
        Log.d(TAG,"handlePurchase | "+purchase.toString());
        Log.d(TAG,"handlePurchase purchaseState: "+purchase.getPurchaseState());

        if(purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED){

            //1.you can provide your entitlement to user before acknowledge,or provide at step 3


            //2.check if is acknowledged,if not,send an acknowledge notification to google
            if(!purchase.isAcknowledged()){
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        //you can set yours bill number or app user id here for a purchase connection
                        .setDeveloperPayload("this is a test developer payload")
                        .build();

                mIabHelper.acknowledge(
                        acknowledgePurchaseParams,
                        new AcknowledgePurchaseResponseListener() {
                            @Override
                            public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                                Log.d(TAG, "onAcknowledgePurchaseResponse: " + billingResult.getResponseCode() + ",debugMsg: " + billingResult.getDebugMessage());
                                if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK){

                                    //3. you can provide entitlement after acknowledge,and I advised it.
                                    //if you provide before acknowledge,as you provide failed,your purchase will not contain a developer payload field.


                                    //after provide,you should check if consume the purchase
                                    consume(purchase);
                                }
                            }
                        });

                  }else{

                //after provide,you should check if consume the purchase
                consume(purchase);
            }
        }
    }



    //consume
    //you should check if the purchase need be consumed,like subscription
    public void consume(final Purchase purchase) {
        Log.d(TAG,"consume | "+purchase.toString());

        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        mIabHelper.consumeAsync(consumeParams, new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                Log.d(TAG, "onConsumeResponse: " + billingResult.getResponseCode() + ",debugMsg: " + billingResult.getDebugMessage());
            }
        });
    }


    public void dispose() {
        if (mIabHelper != null) {
            mIabHelper.dispose();
        }
    }
}
