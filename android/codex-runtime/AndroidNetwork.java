package os.aios.codex;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.*;

/** Export Android trust roots; Bionic provides native Android DNS and sockets. */
final class AndroidNetwork implements AutoCloseable {
    final File caFile;
    AndroidNetwork(Context context)throws Exception {
        caFile=new File(context.getFilesDir(),"android-ca.pem");
        KeyStore roots=KeyStore.getInstance("AndroidCAStore");roots.load(null);
        try(Writer out=new OutputStreamWriter(new FileOutputStream(caFile),StandardCharsets.US_ASCII)){
            Enumeration<String> names=roots.aliases();while(names.hasMoreElements()){
                Certificate cert=roots.getCertificate(names.nextElement());
                out.write("-----BEGIN CERTIFICATE-----\n");out.write(Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(cert.getEncoded()));
                out.write("\n-----END CERTIFICATE-----\n");
            }
        }
    }
    // Native reqwest does not inherit Android's framework ProxySelector.
    static String proxyUrl(Context context) {
        android.net.ConnectivityManager cm=context.getSystemService(android.net.ConnectivityManager.class);
        android.net.ProxyInfo p=cm==null?null:cm.getDefaultProxy();
        if(p==null||p.getHost()==null||p.getHost().isEmpty()||p.getPort()<=0)return "";
        if(p.getPacFileUrl()!=null&&!android.net.Uri.EMPTY.equals(p.getPacFileUrl()))return "";
        try{return new java.net.URI("http",null,p.getHost(),p.getPort(),null,null,null).toASCIIString();}
        catch(java.net.URISyntaxException invalid){throw new IllegalStateException("Android system proxy address is invalid",invalid);}
    }
    static void applyProxy(Map<String,String> env,String proxy) {
        for(String key:List.of("HTTPS_PROXY","https_proxy","HTTP_PROXY","http_proxy","ALL_PROXY","all_proxy"))env.remove(key);
        if(!proxy.isEmpty()){env.put("HTTPS_PROXY",proxy);env.put("HTTP_PROXY",proxy);}
        env.put("NO_PROXY","127.0.0.1,localhost,::1");env.put("no_proxy","127.0.0.1,localhost,::1");
    }
    static String line(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();int b;
        while((b=in.read())!=-1&&b!=10){if(out.size()>16384)throw new IOException("Header too long");if(b!=13)out.write(b);}
        return b==-1&&out.size()==0?null:out.toString(StandardCharsets.US_ASCII);
    }
    public void close() {} 
}
