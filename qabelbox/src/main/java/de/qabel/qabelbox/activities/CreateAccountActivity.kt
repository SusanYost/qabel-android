package de.qabel.qabelbox.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import de.qabel.core.config.Identities
import de.qabel.qabelbox.QabelBoxApplication
import de.qabel.qabelbox.R
import de.qabel.qabelbox.communication.BoxAccountRegisterServer
import de.qabel.qabelbox.communication.callbacks.JsonRequestCallback
import de.qabel.qabelbox.config.AppPreference
import de.qabel.qabelbox.fragments.*
import de.qabel.qabelbox.helper.UIHelper
import de.qabel.qabelbox.identity.interactor.IdentityUseCase
import de.qabel.qabelbox.listeners.IdleCallback
import okhttp3.Response
import org.jetbrains.anko.ctx
import org.jetbrains.anko.longToast
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class CreateAccountActivity : BaseWizardActivity() {

    companion object {
        private val FRAGMENT_ENTER_NAME = 1
        private val FRAGMENT_ENTER_EMAIL = 2
        private val FRAGMENT_ENTER_PASSWORD = 3
    }

    @Inject
    internal lateinit var identityUseCase: IdentityUseCase

    private val TAG = this.javaClass.simpleName

    override val headerFragmentText: String
        get() = mBoxAccountName ?: ""
    override val headerSecondLine: String
        get() = mBoxAccountEMail ?: ""

    private var mBoxAccountName: String? = null
    private var mBoxAccountPassword1: String? = null
    private var mBoxAccountPassword2: String? = null
    private var mBoxAccountEMail: String? = null

    private var existingIdentities: Identities? = null

    private lateinit var mBoxAccountServer: BoxAccountRegisterServer
    private var afterRequest: IdleCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        QabelBoxApplication.getApplicationComponent(applicationContext).inject(this)
        val appPreference = AppPreference(applicationContext)
        mBoxAccountName = appPreference.accountName
        mBoxAccountEMail = appPreference.accountEMail

        super.onCreate(savedInstanceState)
        mBoxAccountServer = BoxAccountRegisterServer(applicationContext, appPreference)

        identityUseCase.getIdentities().subscribe({
            existingIdentities = it
            canExit = it.identities.size > 0
        }, {
            existingIdentities = Identities()
        })
    }

    private fun skipRegister(): Boolean {
        return mBoxAccountName != null && mBoxAccountEMail != null
    }

    override val actionBarTitle: Int
        get() = R.string.headline_create_box_account

    override val wizardEntityLabel: String
        get() = getString(R.string.boxaccount)

    override val fragments: Array<BaseIdentityFragment> =
            arrayOf(CreateAccountMainFragment().apply {
                val bundle = Bundle()
                bundle.putBoolean(CreateAccountMainFragment.SKIP_TO_LOGIN, skipRegister())
                bundle.putString(BaseIdentityFragment.ACCOUNT_NAME, headerFragmentText)
                bundle.putString(BaseIdentityFragment.ACCOUNT_EMAIL, mBoxAccountEMail)
                arguments = bundle
            }, CreateIdentityEditTextFragment.newInstance(R.string.create_account_enter_name_infos, R.string.create_account_name_hint, object : BaseWizardActivity.NextChecker {
                override fun check(view: View): String? {
                    val editText = (view as EditText).text.toString().trim { it <= ' ' }
                    val result = checkBoxAccountName(editText)
                    if (result != null) {
                        return result
                    }
                    setAccountName(editText)
                    return null
                }
            }), CreateIdentityEditTextFragment.newInstance(R.string.create_account_email, R.string.email_hint, object : BaseWizardActivity.NextChecker {
                override fun check(view: View): String? {
                    val editText = (view as EditText).text.toString().trim { it <= ' ' }
                    val inValid = checkEMailAddress(editText)
                    if (inValid != null) {
                        return inValid
                    }
                    mBoxAccountEMail = editText
                    return null
                }
            }, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                    CreateAccountPasswordFragment.newInstance(object : BaseWizardActivity.NextChecker {
                        override fun check(view: View): String? {
                            val editText = (view as EditText).text.toString().trim { it <= ' ' }
                            setPassword(editText, editText)
                            return null
                        }
                    }),
                    CreateAccountFinalFragment())

    private fun checkBoxAccountName(accountName: String): String? {
        if (accountName.length < 1) {
            return getString(R.string.create_account_enter_all_data)
        }
        if (accountName.length > 32) {
            return getString(R.string.create_account_maximum_32_chars)
        }
        return null
    }

    override fun completeWizard() {
        if (existingIdentities?.let { it.entities.size > 0 } ?: false) {
            //fallback if identity exists after box account created. this case should never thrown
            Log.e(TAG, "Identity exist after create box account")
            Toast.makeText(ctx, R.string.skip_create_identity, Toast.LENGTH_SHORT).show()

            finish()
            val i = Intent(ctx, MainActivity::class.java)
            startActivity(i)
            return
        } else {
            val i = Intent(ctx, CreateIdentityActivity::class.java)
            finish()
            startActivity(i)
        }
    }

    private fun setAccountName(text: String) {
        mBoxAccountName = text
        (fragments[3] as CreateAccountPasswordFragment).setAccountName(text)
    }

    private fun register(username: String, password1: String?, password2: String?, email: String?) {
        val dialog = UIHelper.showWaitMessage(this, R.string.dialog_headline_please_wait, R.string.dialog_message_server_communication_is_running, false)
        val callback = createCallback(dialog)
        mBoxAccountServer.register(username, password1, password2, email, callback)
    }

    fun injectIdleCallback(callback: IdleCallback) {
        afterRequest = callback
    }

    fun runIdleCallback(isIdle: Boolean) {
        if (afterRequest == null) {
            return
        }
        if (isIdle) {
            afterRequest!!.idle()
        } else {
            afterRequest!!.busy()
        }
    }

    private fun createCallback(dialog: AlertDialog): JsonRequestCallback {
        runIdleCallback(false)
        return object : JsonRequestCallback(intArrayOf(200, 201, 400)) {

            override fun onError(e: Exception, response: Response?) {
                dialog.dismiss()
                runOnUiThread { Toast.makeText(applicationContext, R.string.server_access_failed_or_invalid_check_internet_connection, Toast.LENGTH_LONG).show() }
                runIdleCallback(true)
            }

            override fun onJSONSuccess(response: Response, json: JSONObject) {
                val result = BoxAccountRegisterServer.parseJson(json)

                //user entered only the username and server send ok
                Log.d(TAG, "step: " + step)
                if (step < FRAGMENT_ENTER_PASSWORD && generateErrorMessage(result).isEmpty()) {
                    showNextUIThread(dialog)
                    runIdleCallback(true)
                    return
                }

                if (result.token != null && result.token.length > 5) {
                    Log.d(TAG, "store token")
                    val appPrefs = AppPreference(ctx)
                    appPrefs.token = result.token
                    appPrefs.accountName = headerFragmentText
                    appPrefs.accountEMail = mBoxAccountEMail
                    showNextUIThread(dialog)
                } else {
                    val errorText = generateErrorMessage(result)
                    dialog.dismiss()
                    longToast(errorText)
                }
                runIdleCallback(true)
            }

            private fun generateErrorMessage(result: BoxAccountRegisterServer.ServerResponse): String {

                val message = ArrayList<String>()
                if (step >= FRAGMENT_ENTER_PASSWORD) {
                    //all dialogs filled out. check all
                    if (result.non_field_errors != null) {
                        message.add(result.non_field_errors)
                    }

                    if (result.password1 != null) {
                        message.add(result.password1)
                    }
                    if (result.password2 != null) {
                        message.add(result.password2)
                    }
                }
                if (step >= FRAGMENT_ENTER_EMAIL) {
                    //only email and username filled out
                    if (result.email != null) {
                        message.add(result.email)
                    }
                }
                if (result.username != null) {
                    message.add(result.username)
                }
                var errorText = ""

                if (message.size == 0) {
                    if (step >= FRAGMENT_ENTER_PASSWORD) {
                        //no token or message, but all fields filled out
                        errorText = getString(R.string.server_access_failed_or_invalid_check_internet_connection)
                    }
                } else {
                    errorText = "- " + message[0]
                    for (i in 1..message.size - 1) {
                        errorText += "\n -" + message[i]
                    }
                }
                return errorText
            }
        }
    }

    private fun showNextUIThread(dialog: AlertDialog) {
        runOnUiThread {
            dialog.dismiss()
            showNextFragment()
        }
    }

    override fun updateActionBar(step: Int) {

        super.updateActionBar(step)
        //override next button text with create identity
        if (step == fragments.size - 1) {
            if (existingIdentities == null || existingIdentities!!.identities.size == 0) {
                nextAction?.setTitle(R.string.btn_create_identity)
            } else {
                nextAction?.setTitle(R.string.finish)
            }
        }
    }

    override fun canShowNext(step: Int): Boolean {
        if (step == FRAGMENT_ENTER_NAME) {
            register(headerFragmentText, null, null, null)
            return false
        }
        if (step == FRAGMENT_ENTER_EMAIL) {
            register(headerFragmentText, null, null, mBoxAccountEMail)
            return false
        }
        if (step == FRAGMENT_ENTER_PASSWORD) {
            register(headerFragmentText, mBoxAccountPassword1, mBoxAccountPassword2, mBoxAccountEMail)
            return false
        } else {
            return true
        }
    }

    private fun setPassword(password1: String, password2: String) {
        mBoxAccountPassword1 = password1
        mBoxAccountPassword2 = password2
    }

}
