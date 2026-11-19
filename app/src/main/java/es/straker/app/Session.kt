package es.straker.app

import android.app.Activity
import android.app.Application
import android.util.Base64
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.*
import java.math.BigInteger
import java.net.URL
import java.security.*
import java.security.spec.RSAPublicKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

private fun decode(value:String)=Base64.decode(value,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
private fun verify(token:String, client:String,nonce:String):String{
    val parts=token.split('.')
    require(parts.size==3)
    val header=JSONObject(String(decode(parts[0])))
    require(header.getString("alg")=="RS256")
    val connection=URL("https://www.googleapis.com/oauth2/v3/certs").openConnection().apply{connectTimeout=8000;readTimeout=8000}
    val keys=connection.getInputStream().bufferedReader().use{JSONObject(it.readText()).getJSONArray("keys")}
    val jwk=(0 until keys.length()).map{keys.getJSONObject(it)}.first{it.getString("kid")==header.getString("kid")}
    val key=KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1,decode(jwk.getString("n"))),BigInteger(1,decode(jwk.getString("e")))))
    val signature=Signature.getInstance("SHA256withRSA").apply{initVerify(key);update((parts[0]+"."+parts[1]).toByteArray(Charsets.US_ASCII))}
    require(signature.verify(decode(parts[2])))
    val claims=JSONObject(String(decode(parts[1])))
    require(claims.getString("iss") in setOf("accounts.google.com","https://accounts.google.com") &&
        claims.getString("aud")==client && claims.getString("nonce")==nonce &&
        claims.getLong("exp")>System.currentTimeMillis()/1000)
    return claims.getString("sub").also{require(it.isNotBlank())}
}
class SessionViewModel(app:Application):AndroidViewModel(app){
    private val prefs=app.getSharedPreferences("profiles",0)
    val owner=MutableStateFlow(prefs.getString("active",null))
    val error=MutableStateFlow("")
    val busy=MutableStateFlow(false)
    private fun activate(id:String){prefs.edit().putString("active",id).apply();owner.value=id;error.value=""}
    fun local(){if(!busy.value)activate("local")}
    fun google(activity:Activity,register:Boolean){
        if(busy.value)return
        val client=activity.getString(R.string.google_web_client_id)
        if(client.isBlank()){error.value="El acceso con Google está pendiente de configuración. Puedes usar el modo local.";return}
        busy.value=true;error.value=""
        viewModelScope.launch{
            try{
                val nonce=Base64.encodeToString(ByteArray(32).also{SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val option=GetSignInWithGoogleOption.Builder(client).setNonce(nonce).build()
                val cred=CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
                require(cred is CustomCredential && cred.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                val token=GoogleIdTokenCredential.createFrom(cred.data).idToken
                val id="google-"+withContext(Dispatchers.IO){verify(token,client,nonce)}
                if(!register && !prefs.contains(id)){error.value="Primero registra este perfil de Google en Straker.";return@launch}
                prefs.edit().putBoolean(id,true).apply();activate(id)
            }catch(e:GetCredentialCancellationException){error.value="Acceso cancelado."}
            catch(e:CancellationException){throw e}
            catch(e:Exception){error.value="No se ha podido verificar la cuenta. Comprueba la conexión y la configuración de Google."}
            finally{busy.value=false}
        }
    }
    fun logout(){
        val wasGoogle=owner.value?.startsWith("google-")==true
        prefs.edit().remove("active").apply();owner.value=null;error.value=""
        if(wasGoogle)viewModelScope.launch{runCatching{CredentialManager.create(getApplication<Application>()).clearCredentialState(ClearCredentialStateRequest())}}
    }
}
