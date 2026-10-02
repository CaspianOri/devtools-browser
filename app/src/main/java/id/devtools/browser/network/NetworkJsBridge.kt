package id.devtools.browser.network

import android.webkit.JavascriptInterface

/**
 * Lane B of the network capture pipeline: the page-facing side.
 *
 * [NETWORK_HOOK_JS] wraps `window.fetch` and `XMLHttpRequest` so every API
 * call the page makes is reported through [onNetworkEvent]. Injected via
 * `evaluateJavascript`, which the embedder may do regardless of the page's
 * Content-Security-Policy.
 *
 * Attach with [INTERFACE_NAME]; call [removeJavascriptInterface] before
 * re-attaching to the same WebView.
 */
class NetworkJsBridge(
    private val tabId: String,
    private val capture: NetworkCapture,
) {
    @JavascriptInterface
    fun onNetworkEvent(json: String) {
        val event = parseJsNetworkEvent(json) ?: return
        capture.recordJsEvent(tabId, event)
    }

    companion object {
        const val INTERFACE_NAME = "DevtoolsBridge"

        /**
         * Installs fetch/XHR wrappers once per page context (guarded by
         * `window.__dtbNetHook`). Reports request/response/error events as
         * JSON to the bridge. Response bodies are read via `text()` /
         * `responseText`, so only text is ever captured here; binary bodies
         * stay out by construction.
         */
        const val NETWORK_HOOK_JS =
            "(function(){" +
                "if(window.__dtbNetHook)return;window.__dtbNetHook=true;" +
                "var seq=0;" +
                "function emit(o){try{DevtoolsBridge.onNetworkEvent(JSON.stringify(o));}catch(e){}}" +
                "function h2o(h){var o={};try{h.forEach(function(v,k){o[k]=v;});}catch(e){}return o;}" +
                "function rawH(raw){var o={};(raw||'').split('\\r\\n').forEach(function(l){" +
                "var i=l.indexOf(':');if(i>0)o[l.slice(0,i).trim()]=l.slice(i+1).trim();});return o;}" +
                // fetch
                "var origFetch=window.fetch;" +
                "if(origFetch){window.fetch=function(input,init){" +
                "var url=typeof input==='string'?input:(input&&input.url)||String(input);" +
                "var method=((init&&init.method)||(input&&input.method)||'GET').toString().toUpperCase();" +
                "var id='f'+(++seq)+'_'+Date.now();" +
                "var reqBody=null;try{if(init&&init.body&&typeof init.body==='string')reqBody=init.body;}catch(e){}" +
                "var reqH={};try{if(init&&init.headers)reqH=h2o(new Headers(init.headers));}catch(e){}" +
                "emit({kind:'request',id:id,url:String(url),method:method,headers:reqH,body:reqBody,api:'fetch'});" +
                "return origFetch.apply(this,arguments).then(function(resp){" +
                "try{resp.clone().text().then(function(t){" +
                "emit({kind:'response',id:id,status:resp.status,headers:h2o(resp.headers),body:t," +
                "mime:resp.headers.get('content-type')||''});" +
                "}).catch(function(){emit({kind:'response',id:id,status:resp.status,headers:h2o(resp.headers)});});" +
                "}catch(e){}" +
                "return resp;" +
                "}).catch(function(err){" +
                "emit({kind:'error',id:id,error:String((err&&err.message)||err)});throw err;});};}" +
                // XHR
                "var XHRP=XMLHttpRequest.prototype;" +
                "var origOpen=XHRP.open,origSend=XHRP.send,origSetH=XHRP.setRequestHeader;" +
                "XHRP.open=function(method,url){this.__dtbId='x'+(++seq)+'_'+Date.now();" +
                "this.__dtbMethod=method;this.__dtbUrl=url;this.__dtbHeaders={};" +
                "return origOpen.apply(this,arguments);};" +
                "XHRP.setRequestHeader=function(k,v){try{this.__dtbHeaders[k]=v;}catch(e){}" +
                "return origSetH.apply(this,arguments);};" +
                "XHRP.send=function(body){" +
                "var xhr=this,id=xhr.__dtbId||('x'+(++seq)+'_'+Date.now());" +
                "var reqBody=(typeof body==='string')?body:null;" +
                "emit({kind:'request',id:id,url:String(xhr.__dtbUrl||'')," +
                "method:String(xhr.__dtbMethod||'GET').toUpperCase(),headers:xhr.__dtbHeaders||{},body:reqBody,api:'xhr'});" +
                "function done(){try{var ct='';try{ct=xhr.getResponseHeader('Content-Type')||'';}catch(e){}" +
                "emit({kind:'response',id:id,status:xhr.status,headers:rawH(xhr.getAllResponseHeaders())," +
                "body:(typeof xhr.responseText==='string')?xhr.responseText:null,mime:ct});}catch(e){}}" +
                "xhr.addEventListener('load',done);" +
                "xhr.addEventListener('error',function(){emit({kind:'error',id:id,error:'xhr error'});});" +
                "xhr.addEventListener('abort',function(){emit({kind:'error',id:id,error:'xhr aborted'});});" +
                "return origSend.apply(this,arguments);};" +
                "})();"
    }
}
