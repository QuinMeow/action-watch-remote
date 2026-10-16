// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.security.SecureRandom;
import java.util.*;

/** Foreground-only BLE remote, no accounts, Internet permission, service, or automatic recording. */
public class MainActivity extends Activity implements RemoteSession.Link {
 private static final UUID SERVICE=uuid("fff0"),NOTIFY=uuid("fff4"),WRITE=uuid("fff5"),CCCD=uuid("2902");
 private static UUID uuid(String s){return UUID.fromString("0000"+s+"-0000-1000-8000-00805f9b34fb");}
 private final Handler h=new Handler(Looper.getMainLooper());
 private final RemoteSession session=new RemoteSession(this);
 private ScanCallback scanCallback;
 private BluetoothAdapter adapter;private BluetoothLeScanner scanner;private BluetoothGatt gatt;private BluetoothGattCharacteristic tx;
 private final ArrayDeque<byte[]> writes=new ArrayDeque<>();private boolean writing,scanning,connecting;private int mtu=23;
 private long setupDeadline,writeDeadline;private int generation;
 private TextView status,details;private Button scan,record,disconnect;private LinearLayout devices;
 private final Set<String> seen=new HashSet<>();
 private long now(){return SystemClock.elapsedRealtime();}
 private final Runnable ticker=new Runnable(){public void run(){session.tick(now());if(connecting&&now()>setupDeadline)close("蓝牙连接/服务初始化超时");if(writing&&now()>writeDeadline)close("蓝牙写入超时；结果未知，请查看相机");render();h.postDelayed(this,500);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(24));root.setBackgroundColor(Color.rgb(14,18,24));scroll.addView(root);
  TextView title=text("ACTION 遥控 · 原型",18);root.addView(title);status=text("未连接",16);root.addView(status);details=text("需先开启相机蓝牙\nOPPO Watch 4 Pro 真机兼容待验证",13);root.addView(details);
  record=button("开始录像");record.setMinHeight(dp(64));root.addView(record);record.setOnClickListener(v->session.toggle(now()));
  scan=button("扫描相机");root.addView(scan);scan.setOnClickListener(v->requestScan());
  disconnect=button("断开连接");root.addView(disconnect);disconnect.setOnClickListener(v->close("已断开；相机录像不会自动停止"));
  devices=new LinearLayout(this);devices.setOrientation(LinearLayout.VERTICAL);root.addView(devices);
  TextView note=text("仅前台遥控。离开应用将断开连接，不会停止相机录像。请以相机屏幕为准。",12);root.addView(note);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  setContentView(scroll);scroll.requestApplyInsets();render();h.post(ticker);
 }
 private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(0,dp(5),0,dp(5));return v;}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(48));b.setContentDescription(s);return b;}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private boolean permissions(){if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 private void requestScan(){
  if(!permissions()){requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);return;}
  try{if(adapter==null){session.message="设备不支持蓝牙";render();return;}if(!adapter.isEnabled()){session.message="请在手表设置开启蓝牙";render();return;}
   close("扫描中…");devices.removeAllViews();seen.clear();scanner=adapter.getBluetoothLeScanner();if(scanner==null){close("蓝牙扫描不可用");return;}
   scanning=true;scan.setEnabled(false);scanCallback=newScanCallback(generation);scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
   int token=generation;h.postDelayed(()->{if(token!=generation)return;stopScan();session.message=seen.isEmpty()?"未发现设备：检查相机蓝牙、定位开关和权限":"扫描结束，请选择你的相机";render();},12000);
  }catch(SecurityException e){close("缺少蓝牙/定位权限，请在设置允许");}catch(RuntimeException e){close("扫描失败："+e.getClass().getSimpleName());}
 }
 @Override public void onRequestPermissionsResult(int code,String[] ps,int[] results){super.onRequestPermissionsResult(code,ps,results);if(code==7){if(permissions())requestScan();else{session.message="权限未允许；可再次扫描或在设置中授权";render();}}}
 private ScanCallback newScanCallback(final int token){return new ScanCallback(){
  @Override public void onScanResult(int callbackType,ScanResult r){h.post(()->{
   if(!scanning||token!=generation)return;
   try{BluetoothDevice d=r.getDevice();String address=d.getAddress();if(seen.contains(address)||seen.size()>=30)return;
    String name=r.getScanRecord()==null?null:r.getScanRecord().getDeviceName();if(name==null)name=d.getName();
    // No service-only filter: camera advertisements differ by firmware. User explicitly chooses.
    if(name==null)name="未命名 BLE 设备";seen.add(address);Button b=button(name+"\n"+address+" · "+r.getRssi()+" dBm");devices.addView(b);b.setOnClickListener(v->connect(d));
   }catch(SecurityException e){close("蓝牙权限已撤回");}
  });}
  @Override public void onScanFailed(int errorCode){h.post(()->{if(scanning&&token==generation)close("扫描失败，代码 "+errorCode+"；稍候重试");});}
 };}
 private void stopScan(){if(scanning&&scanner!=null){try{scanner.stopScan(scanCallback);}catch(SecurityException ignored){}}scanning=false;}
 private void connect(BluetoothDevice d){
  close("正在连接…");devices.removeAllViews();connecting=true;setupDeadline=now()+18000;
  try{gatt=d.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);if(gatt==null)close("无法创建蓝牙连接");}catch(SecurityException e){close("蓝牙权限不足");}render();
 }
 private boolean current(BluetoothGatt g){return g==gatt;}
 private final BluetoothGattCallback callback=new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){h.post(()->{if(!current(g)){try{g.close();}catch(SecurityException ignored){}return;}if(code!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){close("连接已断开 ("+code+")；录像状态未知");return;}if(state==BluetoothProfile.STATE_CONNECTED){try{session.message="发现相机服务…";if(!g.discoverServices())close("服务发现未启动");}catch(SecurityException e){close("蓝牙权限不足");}}});}
  @Override public void onServicesDiscovered(BluetoothGatt g,int code){h.post(()->{if(!current(g))return;try{
   BluetoothGattService s=g.getService(SERVICE);BluetoothGattCharacteristic rx=s==null?null:s.getCharacteristic(NOTIFY);tx=s==null?null:s.getCharacteristic(WRITE);
   if(code!=0||rx==null||tx==null||(rx.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)==0||(tx.getProperties()&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)==0){close("设备没有预期的 DJI BLE 服务");return;}
   if(!g.setCharacteristicNotification(rx,true)){close("无法启用相机通知");return;}
   BluetoothGattDescriptor d=rx.getDescriptor(CCCD);if(d==null){close("缺少通知描述符");return;}d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);if(!g.writeDescriptor(d))close("通知订阅未启动");
  }catch(SecurityException e){close("蓝牙权限不足");}});}
  @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int code){h.post(()->{if(!current(g))return;if(code!=0){close("通知订阅失败："+code);return;}try{if(!g.requestMtu(185))beginPairing();}catch(SecurityException e){close("蓝牙权限不足");}});}
  @Override public void onMtuChanged(BluetoothGatt g,int size,int code){h.post(()->{if(!current(g))return;if(code==0)mtu=Math.max(23,size);beginPairing();});}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){byte[] value=c.getValue();if(value!=null)accept(g,c.getUuid(),value.clone());}
  @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){accept(g,c.getUuid(),value.clone());}
  private void accept(BluetoothGatt g,UUID id,byte[] b){h.post(()->{if(current(g)&&NOTIFY.equals(id))session.receive(b,now());});}
  @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int code){h.post(()->{if(!current(g)||!writing)return;writing=false;if(code!=0){close("BLE写入失败："+code+"；请查看相机");return;}pump();});}
 };
 private void beginPairing(){if(!connecting)return;connecting=false;
  // Android does not expose its local hardware MAC to ordinary apps. Persistent random
  // application identifier is experimental; camera acceptance must be verified on hardware.
  android.content.SharedPreferences prefs=getSharedPreferences("identity",MODE_PRIVATE);String saved=prefs.getString("id",null);byte[] identity=new byte[10];SecureRandom random=new SecureRandom();
  if(saved==null){random.nextBytes(identity);identity[4]=(byte)((identity[4]|2)&254);StringBuilder s=new StringBuilder();for(byte b:identity)s.append(String.format(Locale.ROOT,"%02x",b&255));prefs.edit().putString("id",s.toString()).apply();}else{for(int i=0;i<identity.length;i++)identity[i]=(byte)Integer.parseInt(saved.substring(i*2,i*2+2),16);}
  session.start(identity,random.nextInt(10000),now());
 }
 @Override public void send(byte[] bytes){if(gatt==null||tx==null){close("蓝牙未就绪");return;}int size=Math.max(20,mtu-3);for(int i=0;i<bytes.length;i+=size)writes.add(Arrays.copyOfRange(bytes,i,Math.min(bytes.length,i+size)));pump();}
 private void pump(){if(writing||writes.isEmpty()||gatt==null||tx==null)return;byte[] data=writes.remove();tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);tx.setValue(data);writing=true;writeDeadline=now()+3500;try{if(!gatt.writeCharacteristic(tx))close("BLE写入未被接受；请查看相机");}catch(SecurityException e){close("蓝牙权限已撤回");}}
 @Override public void close(String reason){generation++;stopScan();connecting=false;writing=false;writes.clear();mtu=23;tx=null;BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();}catch(SecurityException ignored){}try{old.close();}catch(SecurityException ignored){}}session.reset();session.message=reason;render();}
 @Override public void changed(){render();}
 private void render(){if(status==null)return;status.setText(session.message);record.setEnabled(session.canRecord(now()));record.setText(session.pendingRecord?"等待相机确认…":session.recording()?"停止录像":"开始录像");record.setContentDescription(record.getText());scan.setEnabled(!scanning&&!connecting&&gatt==null);disconnect.setEnabled(scanning||connecting||gatt!=null);
  if(session.stage==RemoteSession.Stage.READY||session.stage==RemoteSession.Stage.STATUS){details.setText(String.format(Locale.ROOT,"模式 0x%02X · 状态 0x%02X\n相机电量 %s · 时长 %d 秒",session.mode,session.cameraStatus,session.battery>=0&&session.battery<=100?session.battery+"%":"未知",session.seconds));}else details.setText("相机需开机，首次配对需在相机确认\n蓝牙写入成功不代表已开始录像");
 }
 @Override protected void onStop(){super.onStop();close("已离开应用并断开；录像不会自动停止");}
 @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy();}
}
