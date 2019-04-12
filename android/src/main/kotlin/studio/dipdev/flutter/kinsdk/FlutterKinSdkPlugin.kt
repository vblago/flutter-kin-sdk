package studio.dipdev.flutter.kinsdk

import android.app.Activity
import android.content.Context
import com.google.gson.Gson
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import kin.base.Util
import kin.sdk.*
import kin.sdk.exception.CreateAccountException
import kin.utils.ResultCallback


class FlutterKinSdkPlugin(private var activity: Activity, private var context: Context) : MethodCallHandler {

    private lateinit var kinClient: KinClient
    private var isProduction: Boolean? = null
    private var isKinInit = false

    companion object {
        var balanceCallback: EventChannel.EventSink? = null
        var infoCallback: EventChannel.EventSink? = null

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_kin_sdk")
            val instance = FlutterKinSdkPlugin(registrar.activity(), registrar.activity().applicationContext)
            channel.setMethodCallHandler(instance)

            EventChannel(registrar.view(), "flutter_kin_sdk_balance").setStreamHandler(
                    object : EventChannel.StreamHandler {
                        override fun onListen(args: Any?, events: EventChannel.EventSink) {
                            balanceCallback = events
                        }

                        override fun onCancel(args: Any?) {
                        }
                    }
            )

            EventChannel(registrar.view(), "flutter_kin_sdk_info").setStreamHandler(
                    object : EventChannel.StreamHandler {
                        override fun onListen(args: Any?, events: EventChannel.EventSink) {
                            infoCallback = events
                        }

                        override fun onCancel(args: Any?) {
                        }
                    }
            )

        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "initKinClient") {
            isProduction = call.argument("isProduction")
            val appId: String? = call.argument("appId")
            if (isProduction == null) isProduction = false
            if (isProduction == true) {
                sendError("-0", "initKinClient", "Sorry, but the production network is not implemented in this version of plugin")
                return
            }
            initKinClient(appId)
            sendReport("InitKinClient", "Kin init successful")
        } else {
            if (!isKinClientInit()) return
        }

        when {
            call.method == "createAccount" -> {
                result.success(createAccount())
            }

            call.method == "deleteAccount" -> {
                val publicAddress: String? = call.argument("publicAddress") ?: return
                val accountNum: Int = getAccountNum(publicAddress!!) ?: return
                deleteAccount(accountNum)
            }

            call.method == "importAccount" -> {
                val recoveryString: String? = call.argument("recoveryString") ?: return
                val secretPassphrase: String? = call.argument("secretPassphrase") ?: return
                val account: KinAccount? = importAccount(recoveryString!!, secretPassphrase!!)
                        ?: return
                result.success(account!!.publicAddress)
            }

            call.method == "exportAccount" -> {
                val publicAddress: String? = call.argument("publicAddress") ?: return
                val secretPassphrase: String? = call.argument("secretPassphrase") ?: return
                val accountNum: Int = getAccountNum(publicAddress!!) ?: return
                val recoveryString: String? = exportAccount(accountNum, secretPassphrase!!)
                        ?: return
                result.success(recoveryString)
            }

            call.method == "getAccountBalance" -> {
                val publicAddress: String? = call.argument("publicAddress") ?: return
                val accountNum: Int = getAccountNum(publicAddress!!) ?: return
                getAccountBalance(accountNum, fun(balance: Int) { result.success(balance) })

            }

            call.method == "getAccountState" -> {
                val publicAddress: String? = call.argument("publicAddress") ?: return
                val accountNum: Int = getAccountNum(publicAddress!!) ?: return
                getAccountState(accountNum, fun(state: String) { result.success(state) })
            }

            call.method == "sendTransaction" -> {
                var publicAddress: String? = call.argument("publicAddress") ?: return
                var toAddress: String? = call.argument("toAddress") ?: return
                var kinAmount: Int? = call.argument("kinAmount") ?: return
                var memo: String? = call.argument("memo")
                var fee: Int? = call.argument("fee") ?: return

            }

            call.method == "sendWhitelistTransaction" -> {
                var publicAddress: String? = call.argument("publicAddress") ?: return
                var whitelistServiceUrl: String? = call.argument("whitelistServiceUrl") ?: return
                var toAddress: String? = call.argument("toAddress") ?: return
                var kinAmount: Int? = call.argument("kinAmount") ?: return
                var memo: String? = call.argument("memo")
                var fee: Int? = call.argument("fee") ?: return

            }

            call.method == "fund" -> {
                //TODO
            }
        }
    }

    private fun callback(balance: Int) {
    }

    private fun initKinClient(appId: String? = null) {
        if (appId == null) return
        val network: Environment = if (isProduction == true) {
            Environment.PRODUCTION
        } else {
            Environment.TEST
        }

        try {
            kinClient = KinClient(context, network, appId)
            isKinInit = true
        } catch (error: Throwable) {
            sendError("InitKinClient", error)
        }

        receiveAccountsPayments()
    }

    private fun createAccount(): String? {
        try {
            val account: KinAccount = kinClient.addAccount()
            if (!isProduction!!) createAccountOnPlayground(account)
            return account.publicAddress
        } catch (e: CreateAccountException) {
            val err = ErrorReport("CreateAccount", "Account creation exception")
            sendError("-2", "Account creation exception", err)
        }
        return null
    }

    private fun createAccountOnPlayground(account: KinAccount) {
        AccountOnPlayground().onBoard(account, object : AccountOnPlayground.Callbacks {
            override fun onSuccess() {
                sendReport("CreateAccountOnPlaygroundBlockchain", "Account in playground was created successfully", account.publicAddress)
            }

            override fun onFailure(e: Exception) {
                sendError("CreateAccountOnPlaygroundBlockchain", e)
            }
        })
    }

    private fun deleteAccount(accountNum: Int) {
        if (!isAccountCreated()) return
        try {
            kinClient.deleteAccount(accountNum)
            sendReport("DeleteAccount", "Account deletion was a success")
        } catch (error: Throwable) {
            sendError("DeleteAccount", error)
        }
    }

    private fun importAccount(json: String, secretPassphrase: String): KinAccount? {
        try {
            return kinClient.importAccount(json, secretPassphrase)
        } catch (error: Throwable) {
            sendError("ImportAccount", error)
        }
        return null
    }

    private fun exportAccount(accountNum: Int, secretPassphrase: String): String? {
        val account = getAccount(accountNum) ?: return null
        try {
            return account.export(secretPassphrase)
        } catch (error: Throwable) {
            sendError("ExportAccount", error)
        }
        return null
    }

    private fun getAccountBalance(accountNum: Int, completion: (balance: Int) -> Unit) {
        val account = getAccount(accountNum) ?: return
        account.balance.run(
                object : ResultCallback<Balance> {
                    override fun onResult(result: Balance) {
                        completion(result.value().intValueExact())
                    }

                    override fun onError(e: Exception) {
                        sendError("-6", "GetAccountBalance", "Error getting the balance")
                    }
                })
    }

    private fun getAccountState(accountNum: Int, completion: (state: String) -> Unit) {
        val account = getAccount(accountNum) ?: return

        account.status.run(
                object : ResultCallback<Int> {
                    override fun onResult(result: Int?) {
                        when (result) {
                            AccountStatus.CREATED -> completion("Account is created")
                            AccountStatus.NOT_CREATED -> completion("Account is not created")
                        }
                    }

                    override fun onError(e: Exception) {
                        sendError("-15", "GetAccountState", e.localizedMessage)
                    }
                })
    }

    private fun receiveAccountsPayments() {
        if ((!isKinClientInit() || kinClient.accountCount == 0)) {
            return
        }
        for (index in 0 until kinClient.accountCount) {
            receiveAccountPayment(index)
        }
    }

    private fun receiveAccountPayment(accountNum: Int) {
        val account: KinAccount = getAccount(accountNum) ?: return
        account.addPaymentListener { payment ->
            sendReport("PaymentEvent", String.format("to = %s, from = %s", payment.sourcePublicKey(),
                    payment.destinationPublicKey()), payment.amount().toPlainString())
        }
    }

    private fun isKinClientInit(): Boolean {
        if (!isKinInit) {
            sendError("-14", "KinClientInit", "Kin client not inited")
            return false
        }
        return true
    }

    private fun getAccount(accountNum: Int): KinAccount? {
        if (isAccountCreated(accountNum)) {
            return kinClient.getAccount(accountNum)
        }
        return null
    }

    private fun getAccountNum(publicAddress: String): Int? {
        if (kinClient.accountCount == 0) return null
        for (index in 0..(kinClient.accountCount - 1)) {
            if (getAccount(index)?.publicAddress == publicAddress) {
                return index
            }
        }
        sendError("-13", "AccountStateCheck", "Account is not created")
        return null
    }


    private fun isAccountCreated(accountNum: Int = 0): Boolean {
        if (kinClient.accountCount < accountNum) {
            sendError("-13", "AccountStateCheck", "Account is not created")
            return false
        }
        return true
    }


    private fun sendBalance(accountNum: Int, amount: Long) {
        val balanceReport = BalanceReport(accountNum, amount)
        var json: String? = null
        try {
            json = Gson().toJson(balanceReport)
        } catch (e: Throwable) {
            sendError("json", e)
        }
        if (json != null) balanceCallback?.success(json)
    }

    private fun sendReport(type: String, message: String, value: String? = null) {
        val infoReport: InfoReport = if (value != null)
            InfoReport(type, message, value)
        else
            InfoReport(type, message)
        var json: String? = null
        try {
            json = Gson().toJson(infoReport)
        } catch (e: Throwable) {
            sendError("json", e)
        }
        if (json != null) infoCallback?.success(json)
    }

    private fun sendError(type: String, error: Throwable) {
        val err = ErrorReport(type, error.localizedMessage)
        var message: String? = error.message
        if (message == null) message = ""
        sendError(message, error.localizedMessage, err)
    }

    private fun sendError(code: String, type: String, message: String) {
        val err = ErrorReport(type, message)
        sendError(code, message, err)
    }

    private fun sendError(code: String, message: String?, details: ErrorReport) {
        var json: String? = null
        try {
            json = Gson().toJson(details)
        } catch (e: Throwable) {
            sendError("json", e)
        }
        if (json != null) infoCallback?.error(code, message, json)
    }

    data class BalanceReport(val accountNum: Int, val amount: Long)
    data class InfoReport(val type: String, val message: String, val value: String? = null)
    data class ErrorReport(val type: String, val message: String)
}

interface OnStateDeterminated {
    fun onClick()
}

interface OnBalanceDeterminated {
    fun onClick()
}