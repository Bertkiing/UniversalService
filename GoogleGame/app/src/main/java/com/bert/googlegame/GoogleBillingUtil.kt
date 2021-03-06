package com.bert.googlegame

import android.app.Activity
import android.text.TextUtils
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

/**
 *
 * @Author: bertking
 * @ProjectName: GoogleGame
 * @CreateAt: 2020-07-08 15:28
 * @UpdateAt: 2020-07-08 15:28
 * @UpdateRemark: 更新说明
 * @Version: 1.0
 * @Description:
 */
object GoogleBillingUtil {

    val TAG = this::class.java.simpleName

    var IS_DEBUG: Boolean = false

    // 内购ID
    val skus4InApp = ArrayList<String>()

    // 订阅ID
    val sku4Sub = ArrayList<String>()


    val myPurchaseUpdatedListener = MyPurchaseUpdatedListener()


    lateinit var billingClient: BillingClient
    lateinit var builder: BillingClient.Builder


    /******1. 连接操作******/

    /**
     * 连接到 Google Play
     */
    private fun build(activity: Activity) {
        builder = BillingClient.newBuilder(activity)
        billingClient = builder.setListener(myPurchaseUpdatedListener).build()

        if (startConnection()) {
            // 启动查询操作
            queryInAppSkuDetails(skuType = BillingClient.SkuType.INAPP)
            queryInAppSkuDetails(skuType = BillingClient.SkuType.SUBS)
        }
    }


    private fun startConnection(): Boolean {
        if (!::billingClient.isInitialized) {
            Log.d(TAG, "与 Google Play 的连接失败: billingClient 未初始化")
            return false
        } else {
            if (billingClient.isReady) {
                return true
            } else {
                billingClient.startConnection(object : BillingClientStateListener {

                    override fun onBillingSetupFinished(billingResult: BillingResult?) {
                        if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                            // 启动查询操作
                            // The BillingClient is ready. You can query purchases here.

                            queryInAppSkuDetails(skuType = BillingClient.SkuType.INAPP)
                            queryInAppSkuDetails(skuType = BillingClient.SkuType.SUBS)

                        } else {
                            Log.d(
                                TAG,
                                "与 Google Play 的连接失败: code = ${billingResult?.responseCode},msg:${billingResult?.debugMessage}"
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Log.d(TAG, "与 Google Play 的连接失败:onBillingServiceDisconnected")
                    }

                })
                return false
            }
        }
    }


    /***2. 查询应用内商品详情(https://developer.android.com/google/play/billing/billing_library_overview#Query)
     *
     * a.您在配置应用内商品时创建的唯一商品 ID(字符串) 将用于向 Google Play 异步查询应用内商品详情
     * b.要向 Google Play 查询应用内商品的详情，请调用 querySkuDetailsAsync()，两个参数：
     *      1.用于指定商品ID字符串列表
     *      2. SkuType 的 SkuDetailsParams 实例(SkuType 可以是 SkuType.INAPP（针对一次性商品），SkuType.SUBS（针对订阅）)
     *
     *  一次性商品又分为：消耗型 & 非消耗型
     *
     * ******/


    /**
     *
     * 在用户购买商品之前，检索商品价格是一个重要步骤
     *
     * 查询商品详情
     * 一次性商品： BillingClient.SkuType.INAPP
     * 订阅型商品： BillingClient.SkuType.SUBS
     *
     * @param skuType 默认查询一次性商品( BillingClient.SkuType.INAPP)
     */
    private fun queryInAppSkuDetails(skuType: String = BillingClient.SkuType.INAPP) {
        var runnable = Runnable {
            if (!this::billingClient.isInitialized) {
                Log.d(TAG, "与 Google Play 的连接失败: billingClient 未初始化")
                return@Runnable
            }

            val params = SkuDetailsParams.newBuilder()
            params.setType(skuType)

            if (skuType == BillingClient.SkuType.INAPP) {
                params.setSkusList(skus4InApp)
            } else {
                params.setSkusList(sku4Sub)
            }

            billingClient.querySkuDetailsAsync(params.build(), MySkuDetailsResponseListener())
        }

        executeServiceRequest(runnable)
    }


    /**
     * 查询商品信息的回调
     */
    class MySkuDetailsResponseListener : SkuDetailsResponseListener {
        override fun onSkuDetailsResponse(billingResult: BillingResult?, list: MutableList<SkuDetails>?) {

        }

    }


    /****3. 商品的购买(https://developer.android.com/google/play/billing/billing_library_overview#Enable)
     *有些 Android 手机安装的 Google Play 商店应用可能是旧版的，不支持订阅等商品类型。
     * 因此，在应用进入结算流程之前，请调用 isFeatureSupported() 以检查设备是否支持您要销售的商品。要查看商品类型列表
     *
     * 如需从应用发起购买请求，请从界面线程调用 launchBillingFlow() 方法。
     *
     * *******************/


    /**
     * 购买功能
     * @param skuId  商品 ID (skuId)
     * @param skuType 商品类型（SkuType.INAPP - 针对一次性商品，或者 SkuType.SUBS - 针对订阅
     */
    private fun purchase(activity: Activity, skuId: String, skuType: String) {
        if (!this::billingClient.isInitialized) {
            // 购买失败
            Log.d(TAG, "与 Google Play 的连接失败: billingClient 未初始化")
            return
        }

        if (startConnection()) {
            builder.setListener(myPurchaseUpdatedListener)
            val skuList = ArrayList<String>()
            skuList.add(skuId)

            val params = SkuDetailsParams.newBuilder()
                .setSkusList(skuList)
                .setType(skuType)
                .build()

            billingClient.querySkuDetailsAsync(params, object : SkuDetailsResponseListener {
                override fun onSkuDetailsResponse(result: BillingResult?, list: MutableList<SkuDetails>?) {

                    when (result?.responseCode) {
                        // 购买成功
                        BillingClient.BillingResponseCode.OK -> {
                            if (!list.isNullOrEmpty()) {
                                val flowParams = BillingFlowParams.newBuilder().setSkuDetails(list[0]).build()
                                // 当您调用 launchBillingFlow() 方法时，系统会显示 Google Play 购买屏幕
                                billingClient.launchBillingFlow(activity, flowParams)
                            }
                        }

                        BillingClient.BillingResponseCode.USER_CANCELED -> {
                            // 处理取消
                        }

                        else -> {

                        }

                    }

                }
            })


        } else {
            Log.d(TAG, "与 Google Play 的连接失败: billingClient 未初始化")
        }

    }


    /**
     * PurchasesUpdatedListener[https://developer.android.com/reference/com/android/billingclient/api/PurchasesUpdatedListener]
     *
     * 用户购买商品(订阅 & 内购)的回调
     */
    class MyPurchaseUpdatedListener : PurchasesUpdatedListener {
        override fun onPurchasesUpdated(result: BillingResult?, list: MutableList<Purchase>?) {
            if (result?.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
                // 查询成功
            } else {
                // 查询失败
            }
        }

    }


    private fun executeServiceRequest(runnable: Runnable) {
        if (startConnection()) {
            runnable.run()
        }
    }


    /***
     * 一次性商品专有功能：消耗型一次性商品
     * https://developer.android.com/google/play/billing/billing_onetime
     * ************/


    /**
     * 单个消耗商品
     * @param purchaseToken 购买令牌，它是一个唯一标识符，表示用户已购买的单件应用内商品
     */
    private fun consumeAsyn(purchaseToken: String?) {
        if (!this::billingClient.isInitialized) {
            return
        }

        if (purchaseToken.isNullOrEmpty()) {
            throw IllegalArgumentException("purchaseToken is null or empty")
        }

        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        billingClient.consumeAsync(consumeParams, MyConsumeResponseListener())

    }

    /**
     * 多个消耗商品
     * @sample sku 商品ID
     */
    private fun consumAsyn(vararg sku: String) {
        if (!this::billingClient.isInitialized) {
            return
        }

        val skus = Arrays.asList(sku)


        val purchasesResult: Purchase.PurchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        if (purchasesResult?.responseCode == BillingClient.BillingResponseCode.OK) {

        } else {

        }
    }


    /**
     * 一次性消耗商品的回调
     *
     * Google Play 结算服务库会在消耗操作完成时调用该方法。
     *
     * purchaseToken:购买令牌，它是一个唯一标识符，表示用户已购买的单件应用内商品
     *
     */
    class MyConsumeResponseListener : ConsumeResponseListener {
        override fun onConsumeResponse(billingResult: BillingResult?, purchaseToken: String?) {
            if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK) {
                // 购买消耗商品成功
            } else {
                // 购买消耗商品成功
            }
        }

    }


    /***
     *  查询服务
     * 1.查询缓存的购买交易(queryPurchases())
     * 2.查询最近的购买交易(queryPurchaseHistoryAsync())
     */


    /**
     * 本地查询(推荐使用，实时性高)
     *
     * @param skuType 购买类型(SkuType.INAPP 或 SkuType.SUBS)
     *
     * queryPurchases() 方法会使用 Google Play 商店应用的缓存，而不会发起网络请求
     * 至少查询2次(应用启动& onResume)
     */
    fun queryPurchase(skuType: String = BillingClient.SkuType.INAPP): List<Purchase> {
        if (!this::billingClient.isInitialized) {
            return emptyList()
        }

        val purchasesResult = billingClient.queryPurchases(skuType)
        return if (purchasesResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchasesResult.purchasesList
        } else {
            emptyList()
        }

    }

    /**
     * 异步网络历史查询(已过期，已取消或所购商品已被使用)
     * @param skuType
     */
     fun queryPurchaseHistory(skuType: String = BillingClient.SkuType.INAPP) {
        if (!this::billingClient.isInitialized) {
            return
        }

         Runnable {
             billingClient.queryPurchaseHistoryAsync(skuType,MyPurchaseHistoryResponseListener())
         }.run()

    }


    /**
     * 异步网络历史查询回调
     */
    class MyPurchaseHistoryResponseListener : PurchaseHistoryResponseListener {
        override fun onPurchaseHistoryResponse(
            billingResult: BillingResult?,
            purchaseHistoryRecordList: MutableList<PurchaseHistoryRecord>?
        ) {
            if (billingResult?.responseCode == BillingClient.BillingResponseCode.OK && !purchaseHistoryRecordList.isNullOrEmpty()) {
                // 查询成功
            } else {
                // 查询失败
            }
        }
    }


}