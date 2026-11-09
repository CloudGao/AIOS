package os.aios.agent;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import io.github.muntashirakon.adb.*;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** In-process ADB client. Only loopback endpoints and a fixed bootstrap command are exposed. */
final class LocalAdbActivation extends AbsAdbConnectionManager {
 private static LocalAdbActivation instance;
 static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
 static volatile int message=R.string.apk_disconnected;
 static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private final Context context;
 private static final android.os.Handler RECOVERY=new android.os.Handler(android.os.Looper.getMainLooper());
 private static Context recoveryContext;
 private static long nextAttempt,lastAttempt;
 private static int failures;
 private static boolean started;
 static boolean automatic(Context c){return c.getSharedPreferences("local_adb",0).getBoolean("automatic",true);}
 static void automatic(Context c,boolean enabled){c.getSharedPreferences("local_adb",0).edit().putBoolean("automatic",enabled).apply();startRecovery(c);wakeRecovery();}
 static void startRecovery(Context c){
  Context app=c.getApplicationContext();
  RECOVERY.post(()->{
   if(started)return;started=true;recoveryContext=app;
   AdbBridge.onDeath(LocalAdbActivation::wakeRecovery);
   android.content.IntentFilter filter=new android.content.IntentFilter();
   filter.addAction(android.content.Intent.ACTION_USER_PRESENT);filter.addAction(android.content.Intent.ACTION_SCREEN_ON);
   filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
   app.registerReceiver(new android.content.BroadcastReceiver(){public void onReceive(Context c,android.content.Intent i){wakeRecovery();}},filter,Context.RECEIVER_NOT_EXPORTED);
   RECOVERY.postDelayed(CHECK,3000);
  });
 }
 private static void wakeRecovery(){RECOVERY.post(()->{nextAttempt=0;RECOVERY.removeCallbacks(CHECK);RECOVERY.post(CHECK);});}
 private static boolean allowed(Context c){
  return automatic(c)&&c.getSharedPreferences("local_adb",0).getBoolean("paired",false)
   &&c.getSystemService(android.os.UserManager.class).isUserUnlocked()
   &&android.provider.Settings.Global.getInt(c.getContentResolver(),"adb_wifi_enabled",0)==1;
 }
 private static final Runnable CHECK=new Runnable(){public void run(){
  RECOVERY.removeCallbacks(this);RECOVERY.postDelayed(this,15000);
  Context c=recoveryContext;if(c==null)return;
  if(AdbBridge.connected()){failures=0;nextAttempt=0;return;}
  long now=android.os.SystemClock.elapsedRealtime();
  if(!allowed(c)||now<nextAttempt||(lastAttempt!=0&&now-lastAttempt<15000)||!BUSY.compareAndSet(false,true))return;
  lastAttempt=now;
  WORK.execute(()->{
   boolean attempted=false,success=false;
   try{
    if(!allowed(c)||!Authorization.enabled(c))return;
    attempted=true;message=R.string.adb_connecting;
    android.util.Log.i("AIOSLocalAdb","recovery_attempt");
    get(c).activate(0);success=AdbBridge.connected();
    message=AdbBridge.monitorsInput()?R.string.apk_connected:R.string.apk_connected_without_monitor;
    android.util.Log.i("AIOSLocalAdb","recovery_connected");
   }catch(Exception e){message=R.string.adb_connect_failed;android.util.Log.w("AIOSLocalAdb","recovery_failed: "+e.getClass().getSimpleName());}
   finally{
    final boolean tried=attempted,ok=success;
    RECOVERY.post(()->{if(tried){failures=ok?0:Math.min(failures+1,6);nextAttempt=android.os.SystemClock.elapsedRealtime()+(ok?15000:Math.min(300000L,15000L<<(failures-1)));}BUSY.set(false);});
   }
  });
 }};
 private PrivateKey privateKey;
 private Certificate certificate;
 private AdbStream bootstrap;
 static synchronized LocalAdbActivation get(Context c)throws Exception{
  if(instance==null)instance=new LocalAdbActivation(c.getApplicationContext());return instance;
 }
 private LocalAdbActivation(Context c)throws Exception{
  context=c;setApi(Build.VERSION.SDK_INT);setHostAddress("127.0.0.1");setTimeout(12000,TimeUnit.MILLISECONDS);setThrowOnUnauthorised(true);
  AtomicFile identity=new AtomicFile(new File(c.getNoBackupFilesDir(),"local-adb-identity.bin"));
  if(identity.getBaseFile().exists()){
   try(DataInputStream in=new DataInputStream(identity.openRead())){
    int n=in.readInt();if(n<1||n>16384)throw new IOException("invalid_identity");byte[] key=new byte[n];in.readFully(key);
    privateKey=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));java.util.Arrays.fill(key,(byte)0);
    n=in.readInt();if(n<1||n>16384)throw new IOException("invalid_certificate");byte[] cert=new byte[n];in.readFully(cert);
    certificate=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
   }
  }else{
   KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);java.security.KeyPair pair=generator.generateKeyPair();privateKey=pair.getPrivate();
   X500Name name=new X500Name("CN=AIOS Local ADB");long now=System.currentTimeMillis();
   byte[] cert=new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()).add(BigInteger.ONE),new Date(now-86400000L),new Date(now+10L*365*86400000L),name,pair.getPublic())
     .build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(new BouncyCastleProvider()).build(privateKey)).getEncoded();
   certificate=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cert));
   FileOutputStream raw=identity.startWrite();
   try{DataOutputStream out=new DataOutputStream(raw);byte[] key=privateKey.getEncoded();out.writeInt(key.length);out.write(key);java.util.Arrays.fill(key,(byte)0);out.writeInt(cert.length);out.write(cert);out.flush();identity.finishWrite(raw);}
   catch(Exception failure){identity.failWrite(raw);throw failure;}
  }
 }
 protected PrivateKey getPrivateKey(){return privateKey;}
 protected Certificate getCertificate(){return certificate;}
 protected String getDeviceName(){return "AIOS-local";}
 static int port(String text,boolean optional){
  if(optional&&text.trim().isEmpty())return 0;
  if(!text.matches("[0-9]{1,5}"))throw new IllegalArgumentException("invalid_port");int n=Integer.parseInt(text);if(n<1||n>65535)throw new IllegalArgumentException("invalid_port");return n;
 }
 void pairLocal(int port,String code)throws Exception{
  if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("invalid_pairing_code");
  if(!pair("127.0.0.1",port,code))throw new IOException("pair_failed");
  context.getSharedPreferences("local_adb",0).edit().putBoolean("paired",true).apply();
 }
 private int discover()throws Exception{
  CountDownLatch found=new CountDownLatch(1);AtomicInteger port=new AtomicInteger();
  AdbMdns discovery=new AdbMdns(context,AdbMdns.SERVICE_TYPE_TLS_CONNECT,(host,p)->{if(host!=null&&p>0){port.set(p);found.countDown();}});
  try{discovery.start();if(!found.await(10,TimeUnit.SECONDS))throw new IOException("local_port_not_found");return port.get();}
  finally{discovery.stop();}
 }
 void activate(int requestedPort)throws Exception{
  if(AdbBridge.connected())return;
  if(bootstrap!=null){bootstrap.close();bootstrap=null;}
  disconnect();int p=requestedPort==0?discover():requestedPort;
  try {
  if(!connect("127.0.0.1",p))throw new IOException("local_connect_failed");
  String apk=context.getApplicationInfo().sourceDir;
  if(!apk.matches("/data/app/[A-Za-z0-9_=/+.~\\-]+/base\\.apk"))throw new IOException("invalid_apk_path");
  bootstrap=openStream("shell:CLASSPATH='"+apk+"' nohup setsid app_process /system/bin os.aios.apk.shell.ShellMain </dev/null >/data/local/tmp/aios-apk-shell.log 2>&1 & wait");
  long deadline=android.os.SystemClock.elapsedRealtime()+15000;
  while(!AdbBridge.connected()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
  if(!AdbBridge.connected())throw new IOException("bootstrap_not_ready");
  } finally {try{if(bootstrap!=null)bootstrap.close();}finally{bootstrap=null;disconnect();}}
 }
}
