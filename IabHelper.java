package com.tencent.imsdk.samples;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import java.util.ArrayList;
import java.util.List;

/**
 * google play billing library封装
 * @author zachzeng
 */
public class IabHelper implements PurchasesUpdatedListener{
    public static final String TAG = "IabHelper";

    //自定义新增错误码
    public static final int IAB_BILLING_CLIENT_NULL = -3000;

    private BillingClient mBillingClient;
    private OnIabPurchaseListener mPurchaseListener;

    //true if billing service is connected
    private boolean mIsServiceConnected = false;


    public IabHelper(Context context){
        mBillingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();
    }


    /**
     * connect to billing service
     * @param listener
     */
    public void startSetup(final OnIabSetupFinishedListener listener){
        Log.i(TAG,"startSetup");
        startServiceConnection(new IabRunnable() {
            @Override
            public void run(BillingResult result) {
                if(listener != null){
                    listener.onIabSetupFinished(result);
                }
            }
        });
    }


    /**
     * start a purchase or subscription replace flow
     * @param billingFlowParams
     */
    public void launchPurchaseFlow(final Activity activity,final BillingFlowParams billingFlowParams, final OnIabPurchaseListener listener){
        Log.i(TAG,"launchPurchaseFlow");

        if(listener == null){
            Log.e(TAG,"launchPurchaseFlow: listener is null.");
            return;
        }

        //cached
        mPurchaseListener = listener;

        IabRunnable runnable = new IabRunnable() {
            @Override
            public void run(BillingResult result) {
                if(mBillingClient != null){
                    Log.i(TAG,"Launching in-app purchase flow, Replace old SKU ? "+(billingFlowParams.getOldSku() != null));
                    mBillingClient.launchBillingFlow(activity,billingFlowParams);
                }else{
                    Log.e(TAG,"launchPurchaseFlow: BillingClient is null.");

                    BillingResult billingResult = BillingResult.newBuilder()
                            .setResponseCode(IAB_BILLING_CLIENT_NULL)
                            .setDebugMessage("BillingClient is null.")
                            .build();

                    listener.onPurchaseResponse(billingResult, null);
                }
            }
        };

        executeServiceRequest(runnable);
    }


    /**
     * query sku details
     * @param skuDetailsParams
     * @param listener
     */
    public void querySkuDetailsAsync(final SkuDetailsParams skuDetailsParams, final SkuDetailsResponseListener listener){
        Log.i(TAG,"querySkuDetailsAsync");

        if(listener == null){
            Log.e(TAG,"querySkuDetailsAsync: listener is null.");
            return;
        }

        IabRunnable iabRunnable = new IabRunnable() {
            @Override
            public void run(BillingResult result) {
                if(mBillingClient != null){
                    mBillingClient.querySkuDetailsAsync(skuDetailsParams,listener);
                }else{
                    Log.e(TAG,"querySkuDetailsAsync: BillingClient is null.");

                    BillingResult billingResult = BillingResult.newBuilder()
                            .setResponseCode(IAB_BILLING_CLIENT_NULL)
                            .setDebugMessage("BillingClient is null.")
                            .build();
                    listener.onSkuDetailsResponse(billingResult, null);
                }
            }
        };

        executeServiceRequest(iabRunnable);
    }


    /**
     * query purchases,block till the result back
     * @return
     */
    public void queryPurchasesAsync(final OnIabQueryPurchasesListener listener){
        Log.i(TAG,"queryPurchasesAsync");

        if(listener == null){
            Log.e(TAG,"queryPurchasesAsync: listener is null.");
            return;
        }

        IabRunnable iabRunnable = new IabRunnable() {
            @Override
            public void run(BillingResult result) {

               if(mBillingClient != null){
                   ArrayList<Purchase> resultList = new ArrayList<Purchase>();

                    //1. query in-app
                   Purchase.PurchasesResult inAppResult = mBillingClient.queryPurchases(BillingClient.SkuType.INAPP);

                   //add to result list
                   if (inAppResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                       List<Purchase> inAppPurchaseList = inAppResult.getPurchasesList();
                       if(inAppPurchaseList != null && !inAppPurchaseList.isEmpty()) {
                           resultList.addAll(inAppPurchaseList);
                       }
                   }else{
                       Log.e(TAG,"queryPurchasesAsync: Get an error response trying to query in-app purchases.");
                   }


                   //2. query subs
                   if(isSubscriptionSupported()){
                       Purchase.PurchasesResult subResult = mBillingClient.queryPurchases(BillingClient.SkuType.SUBS);

                       //add to result list
                       if(subResult.getResponseCode() == BillingClient.BillingResponseCode.OK){
                           List<Purchase> subPurchaseList = subResult.getPurchasesList();
                           if(subPurchaseList != null && !subPurchaseList.isEmpty()) {
                               resultList.addAll(subPurchaseList);
                           }
                       }else{
                           Log.e(TAG,"queryPurchasesAsync: Get an error response trying to query subscription purchases.");
                       }
                   }else{
                       Log.i(TAG,"queryPurchasesAsync: don't support subscription.");
                   }

                   //callback
                   listener.onQueryPurchasesResponse(inAppResult.getBillingResult(), resultList);

               }else {
                   Log.e(TAG,"queryPurchasesAsync: BillingClient is null.");

                   BillingResult billingResult = BillingResult.newBuilder()
                           .setResponseCode(IAB_BILLING_CLIENT_NULL)
                           .setDebugMessage("BillingClient is null.")
                           .build();
                   listener.onQueryPurchasesResponse(billingResult, null);
               }
            }
        };

        executeServiceRequest(iabRunnable);
    }


    /**
     * consume special purchase token
     * if have more than one purchase,this api will be called multiply times.
     * @param params
     * @param listener
     */
    public void consumeAsync(final ConsumeParams params, final ConsumeResponseListener listener){
        Log.i(TAG,"consumeAsync");

        if(listener == null){
            Log.e(TAG,"consumeAsync: listener is null.");
            return;
        }

        IabRunnable iabRunnable = new IabRunnable() {
            @Override
            public void run(final BillingResult result) {
                if(mBillingClient != null){
                    mBillingClient.consumeAsync(params,listener);
                }else{
                    Log.e(TAG,"consumeAsync: BillingClient is null.");
                    BillingResult billingResult = BillingResult.newBuilder()
                            .setResponseCode(IAB_BILLING_CLIENT_NULL)
                            .setDebugMessage("BillingClient is null.")
                            .build();
                    listener.onConsumeResponse(billingResult, "");
                }
            }
        };

        executeServiceRequest(iabRunnable);
    }


    /**
     * NEW: load rewarded sku
     * @param params
     * @param listener
     */
//    public void loadRewardedSku(final RewardLoadParams params, final RewardResponseListener listener){
//        Log.i(TAG,"loadRewardedSku");
//        IabRunnable iabRunnable = new IabRunnable() {
//            @Override
//            public void run(BillingResult result) {
//                if(mBillingClient != null){
//                    mBillingClient.loadRewardedSku(params, new RewardResponseListener() {
//                        @Override
//                        public void onRewardResponse(BillingResult billingResult) {
//
//                        }
//                    });
//                }else{
//                    Log.e(TAG,"loadRewardedSku billingClient null.");
//                    BillingResult res = BillingResult.newBuilder()
//                            .setResponseCode(BILLING_CLIENT_NULL)
//                            .build();
//                    listener.onRewardResponse(res);
//                }
//            }
//        };
//
//        executeServiceRequest(iabRunnable);
//    }


    /**
     * query history purchases
     * @param skuType
     * @param listener
     */
    public void queryPurchaseHistoryAsync(
            final @BillingClient.SkuType String skuType, final PurchaseHistoryResponseListener listener){
        Log.i(TAG,"queryPurchaseHistoryAsync");

        if(listener == null){
            Log.e(TAG,"queryPurchaseHistoryAsync: listener is null.");
            return;
        }


        IabRunnable iabRunnable = new IabRunnable() {
            @Override
            public void run(BillingResult result) {
                if(mBillingClient != null){
                    mBillingClient.queryPurchaseHistoryAsync(skuType,listener);
                }else{
                    Log.e(TAG,"queryPurchaseHistoryAsync: BillingClient null.");
                    BillingResult res = BillingResult.newBuilder()
                            .setResponseCode(IAB_BILLING_CLIENT_NULL)
                            .setDebugMessage("BillingClient is null.")
                            .build();
                    listener.onPurchaseHistoryResponse(res, null);
                }
            }
        };

        executeServiceRequest(iabRunnable);
    }


    /**
     * NEW: send a acknowledge notification to google,after finish a purchase.
     * consumeAsync will default send a acknowledge notification
     * @param acknowledgePurchaseParams you can set a developer payload in it
     * @param listener
     */
    public void acknowledge(final AcknowledgePurchaseParams acknowledgePurchaseParams, final AcknowledgePurchaseResponseListener listener){
        Log.i(TAG,"acknowledge");

        if(listener == null){
            Log.e(TAG,"acknowledge: listener is null.");
            return;
        }

        IabRunnable runnable = new IabRunnable() {
            @Override
            public void run(BillingResult result) {
                if(mBillingClient != null){
                    mBillingClient.acknowledgePurchase(acknowledgePurchaseParams,listener);
                }else{
                    Log.e(TAG,"acknowledge: BillingClient null.");
                    BillingResult res = BillingResult.newBuilder()
                            .setResponseCode(IAB_BILLING_CLIENT_NULL)
                            .setDebugMessage("BillingClient is null.")
                            .build();
                    listener.onAcknowledgePurchaseResponse(res);
                }
            }
        };

        executeServiceRequest(runnable);

    }


    private boolean isSubscriptionSupported(){
        if(mBillingClient != null){
            BillingResult result = mBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);
            return  result.getResponseCode() == BillingClient.BillingResponseCode.OK;
        }
        return false;
    }


    /**
     * dispose
     */
    public void dispose(){
        Log.i(TAG,"dispose");
        if(mBillingClient != null && mBillingClient.isReady()){
            mBillingClient.endConnection();
            mIsServiceConnected = false;
            mBillingClient = null;
            mPurchaseListener = null;
        }
    }


    /**
     * AIDL Connect
     * @param iabRunnable
     */
    private void startServiceConnection(final IabRunnable iabRunnable) {
        if(mIsServiceConnected){
            Log.i(TAG,"Service is connected.");
            return;
        }

        if(mBillingClient != null) {
            mBillingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult billingResult) {
                    int billingResponseCode = billingResult.getResponseCode();
                    if (billingResponseCode == BillingClient.BillingResponseCode.OK) {
                        mIsServiceConnected = true;
                    }

                    iabRunnable.run(billingResult);
                }


                @Override
                public void onBillingServiceDisconnected() {
                    mIsServiceConnected = false;

                    BillingResult result = BillingResult.newBuilder()
                            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                            .build();
                    iabRunnable.run(result);
                }
            });
        }else{
            Log.e(TAG,"startServiceConnection: BillingClient is null.");
            BillingResult result = BillingResult.newBuilder()
                    .setResponseCode(IAB_BILLING_CLIENT_NULL)
                    .setDebugMessage("BillingClient is null.")
                    .build();
            iabRunnable.run(result);
        }
    }


    private void executeServiceRequest(IabRunnable runnable){
        if(mIsServiceConnected){
            runnable.run(null);
        }else {
            //if billing service was disconnected,we try to reconnect 1 time.
            startServiceConnection(runnable);
        }
    }


    /********************************* onPurchasesUpdated *************************************/

    /**
     * purchased callback
     * with pending transaction,this will be called multiply times!
     * @param billingResult
     * @param purchases
     */
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
        Log.i(TAG,"onPurchasesUpdated");
        if(mPurchaseListener != null){
            mPurchaseListener.onPurchaseResponse(billingResult, purchases);
        }
    }

    /********************************* API Callback *************************************/

    interface IabRunnable{
        void run(BillingResult result);
    }

    //set up callback
    public interface OnIabSetupFinishedListener{
        void onIabSetupFinished(BillingResult result);
    }

    //query purchases callback
    public interface OnIabQueryPurchasesListener{
        void onQueryPurchasesResponse(BillingResult result, List<Purchase> purchasesList);
    }

    //pay callback
    public  interface OnIabPurchaseListener{
        void onPurchaseResponse(BillingResult result, List<Purchase> purchasesList);
    }
}
