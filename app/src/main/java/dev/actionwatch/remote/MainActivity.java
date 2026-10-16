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
 private TextView status,details,gpsDetails;private Button scan,record,disconnect,modeButton,gpsButton,simButton,simTools;
 private AlertDialog activeDialog;
 private GpsController gps;private final GpsData.Gate gpsGate=new GpsData.Gate();
 private SimulatedCamera fake;private boolean simulationGps,freezeSimulationGps;
 private GpsData.Fix fakeFix;private long nextFakeFix;private long gpsSent;
 private int gpsPermissionGeneration=-1;private LinearLayout devices;
 private final Set<String> seen=new HashSet<>();
 private long now(){return SystemClock.elapsedRealtime();}
 private final Runnable gpsTicker=new Runnable(){public void run(){pushGps();h.postDelayed(this,100);}};
 private final Runnable ticker=new Runnable(){public void run(){if(fake!=null)for(byte[] b:fake.tick(now()))session.receive(b,now());session.tick(now());if(connecting&&now()>setupDeadline)close("蓝牙连接/服务初始化超时");if(writing&&now()>writeDeadline)close("蓝牙写入超时；结果未知，请查看相机");render();h.postDelayed(this,500);}};
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(24));root.setBackgroundColor(Color.rgb(14,18,24));scroll.addView(root);
  TextView title=text("Action 遥控 0.2",18);root.addView(title);status=text("未连接",16);root.addView(status);details=text("开启相机蓝牙后连接",13);root.addView(details);
  record=button("开始录像");record.setMinHeight(dp(64));root.addView(record);record.setOnClickListener(v->session.toggle(now()));
  modeButton=button("切换模式");root.addView(modeButton);modeButton.setOnClickListener(v->chooseMode());
  gpsButton=button("开启GPS推送");root.addView(gpsButton);gpsButton.setOnClickListener(v->toggleGps());
  gpsDetails=text("GPS已关闭",12);root.addView(gpsDetails);
  simButton=button("连接模拟设备");root.addView(simButton);simButton.setOnClickListener(v->startSimulation());
  simTools=button("模拟测试");root.addView(simTools);simTools.setOnClickListener(v->simulationOptions());
  gps=new GpsController(this,()->render());
  scan=button("扫描相机");root.addView(scan);scan.setOnClickListener(v->requestScan());
  disconnect=button("断开连接");root.addView(disconnect);disconnect.setOnClickListener(v->close("已断开"));
  devices=new LinearLayout(this);devices.setOrientation(LinearLayout.VERTICAL);root.addView(devices);
  TextView note=text("退出后停止GPS；录像请在相机结束",12);root.addView(note);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  setContentView(scroll);scroll.requestApplyInsets();render();
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
 @Override public void onRequestPermissionsResult(int code,String[] ps,int[] results){super.onRequestPermissionsResult(code,ps,results);if(code==8){if(gpsPermissionGeneration==generation&&fake==null&&session.fresh(now())){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)gps.start();else gps.message="未获精确定位，GPS保持关闭";}gpsPermissionGeneration=-1;render();return;}if(code==7){if(permissions())requestScan();else{session.message="权限未允许；可再次扫描或在设置中授权";render();}}}
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
  @Override public void onConnectionStateChange(BluetoothGatt g,int code,int state){h.post(()->{if(!current(g)){try{g.close();}catch(SecurityException ignored){}return;}if(code!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){close("连接已断开 ("+code+")，请查看相机");return;}if(state==BluetoothProfile.STATE_CONNECTED){try{session.message="发现相机服务…";if(!g.discoverServices())close("服务发现未启动");}catch(SecurityException e){close("蓝牙权限不足");}}});}
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
 @Override public void send(byte[] bytes){if(fake!=null){SimulatedCamera peer=fake;int token=generation;List<byte[]> reply=peer.accept(bytes,now());h.post(()->{if(token==generation&&fake==peer)for(byte[] b:reply)session.receive(b,now());});return;}if(gatt==null||tx==null){close("蓝牙未就绪");return;}int size=Math.max(20,mtu-3);for(int i=0;i<bytes.length;i+=size)writes.add(Arrays.copyOfRange(bytes,i,Math.min(bytes.length,i+size)));pump();}
 private void pump(){if(writing||writes.isEmpty()||gatt==null||tx==null)return;byte[] data=writes.remove();tx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);tx.setValue(data);writing=true;writeDeadline=now()+3500;try{if(!gatt.writeCharacteristic(tx))close("BLE写入未被接受；请查看相机");}catch(SecurityException e){close("蓝牙权限已撤回");}}
 @Override public void close(String reason){if(activeDialog!=null)activeDialog.dismiss();generation++;gpsGate.reset();gpsPermissionGeneration=-1;if(gps!=null)gps.stop();fake=null;simulationGps=false;freezeSimulationGps=false;fakeFix=null;gpsSent=0;stopScan();connecting=false;writing=false;writes.clear();mtu=23;tx=null;BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();}catch(SecurityException ignored){}try{old.close();}catch(SecurityException ignored){}}session.reset();session.message=reason;render();}
 @Override public void changed(){render();}
 private void render(){if(status==null)return;
  boolean hasTransport=gatt!=null||fake!=null||connecting;
  int controls=hasTransport?View.VISIBLE:View.GONE;
  record.setVisibility(controls);modeButton.setVisibility(controls);gpsButton.setVisibility(controls);gpsDetails.setVisibility(controls);
  scan.setVisibility(hasTransport?View.GONE:View.VISIBLE);scan.setText(scanning?"扫描中…":"扫描相机");
  simButton.setVisibility(hasTransport||scanning?View.GONE:View.VISIBLE);
  disconnect.setVisibility(hasTransport||scanning?View.VISIBLE:View.GONE);
  status.setText((fake!=null?"模拟设备 · ":"")+session.message);record.setEnabled(session.canRecord(now()));record.setText(session.pendingRecord?"等待相机确认…":session.recording()?"停止录像":"开始录像");record.setContentDescription(record.getText());scan.setEnabled(!scanning&&!connecting&&gatt==null&&fake==null);disconnect.setEnabled(scanning||connecting||gatt!=null||fake!=null);
  modeButton.setEnabled(session.canSwitchMode(now()));modeButton.setText(session.pendingMode?"正在切换…":session.mode<0?"模式":"模式："+RemoteSession.modeName(session.mode));modeButton.setContentDescription(modeButton.getText());
  boolean gpsOn=simulationGps||(gps!=null&&gps.active());gpsButton.setEnabled(gpsOn||session.fresh(now()));gpsButton.setText(gpsOn?"关闭GPS":fake!=null?"开启模拟GPS":"开启GPS");gpsButton.setContentDescription(gpsButton.getText());
  simButton.setEnabled(gatt==null&&!scanning&&!connecting&&fake==null);simTools.setVisibility(fake==null?View.GONE:View.VISIBLE);
  GpsData.Fix f=fake!=null?fakeFix:gps==null?null:gps.fix();String validity=GpsData.invalidReason(f,now());
  String gpsState=fake!=null?(simulationGps?"模拟GPS":"模拟GPS已关闭"):(gps==null?"GPS已关闭":gps.message);
  gpsDetails.setText(gpsOn?String.format(Locale.CHINA,"%s · %s\n采样 %.1f Hz · 发送上限 10 Hz\n已发送 %d 帧",gpsState,validity==null?"定位有效":"等待定位",fake!=null?1.0:gps.sampleHz,gpsSent):gpsState);
  if(session.stage==RemoteSession.Stage.READY||session.stage==RemoteSession.Stage.STATUS){details.setText(String.format(Locale.ROOT,"%s · 电量 %s\n时长 %d 秒",RemoteSession.modeName(session.mode),session.battery>=0&&session.battery<=100?session.battery+"%":"未知",session.seconds));}else details.setText("请先连接相机");
 }
 private void showDialog(AlertDialog.Builder builder){if(activeDialog!=null)activeDialog.dismiss();activeDialog=builder.create();activeDialog.setOnDismissListener(d->{if(activeDialog==d)activeDialog=null;});activeDialog.show();}
 private void chooseMode(){
  if(!session.canSwitchMode(now()))return;
  final int token=generation;final int[] values={1,0,2,5,10,40};String[] labels=new String[values.length];for(int i=0;i<values.length;i++)labels[i]=RemoteSession.modeName(values[i]);
  showDialog(new AlertDialog.Builder(this).setTitle("选择模式").setItems(labels,(d,index)->{if(token==generation)session.switchMode(values[index],now());}).setNegativeButton("取消",null));
 }
 private void toggleGps(){
  if(simulationGps||(gps!=null&&gps.active())){simulationGps=false;fakeFix=null;gpsGate.reset();gps.stop();render();return;}
  if(!session.fresh(now()))return;
  if(fake!=null){gpsGate.reset();simulationGps=true;freezeSimulationGps=false;nextFakeFix=0;render();return;}
  int token=generation;
  showDialog(new AlertDialog.Builder(this).setTitle("开启GPS推送？").setMessage("将手表位置、速度和高度发给当前相机。退出应用或断开时停止。").setPositiveButton("开启",(d,w)->{
   if(token!=generation||fake!=null||!session.fresh(now()))return;
   if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){gpsPermissionGeneration=generation;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},8);}else gps.start();
  }).setNegativeButton("取消",null));
 }
 private void pushGps(){
  long t=now();
  if(fake!=null&&simulationGps&&!freezeSimulationGps&&t>=nextFakeFix){nextFakeFix=t+1000;fakeFix=new GpsData.Fix(System.currentTimeMillis(),t,116.391,39.907,50,1,0,0,3,4,0.5,12);}
  boolean on=fake!=null?simulationGps:gps!=null&&gps.active();if(!on)return;
  if(fake==null&&session.powerMode==3){gps.stop();gps.message="相机休眠，GPS推送已停止";render();return;}
  if(fake==null&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){gps.stop();gps.message="定位权限已撤回，推送停止";render();return;}
  GpsData.Fix f=fake!=null?fakeFix:gps.fix();
  boolean linkReady=(fake!=null||(!writing&&writes.isEmpty()&&gatt!=null))&&!session.pendingRecord&&!session.pendingMode;
  byte[] payload=gpsGate.poll(f,t,session.fresh(t),linkReady);
  if(payload!=null&&session.sendGps(payload,t))gpsSent++;
 }
 private void startSimulation(){
  if(gatt!=null||scanning||connecting)return;
  close("启动本机模拟");fake=new SimulatedCamera();session.start(new byte[10],1234,now());render();
 }
 private void simulationOptions(){
  if(fake==null)return;final SimulatedCamera peer=fake;
  String[] options={"恢复正常响应","下条指令：拒绝","下条指令：仅应答","下条指令：丢包","下条指令：错序应答","暂停/恢复状态","暂停/恢复GPS","模拟断连"};
  showDialog(new AlertDialog.Builder(this).setTitle("模拟测试").setItems(options,(d,index)->{if(fake!=peer)return;if(index<5)peer.nextFault=SimulatedCamera.Fault.values()[index];else if(index==5)peer.sendStatus=!peer.sendStatus;else if(index==6)freezeSimulationGps=!freezeSimulationGps;else close("模拟设备已断开");render();}).setNegativeButton("取消",null));
 }
 @Override protected void onStart(){super.onStart();h.removeCallbacks(ticker);h.removeCallbacks(gpsTicker);h.post(ticker);h.post(gpsTicker);}
 @Override protected void onStop(){super.onStop();h.removeCallbacks(ticker);h.removeCallbacks(gpsTicker);close("已断开，请重新连接");}
 @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy();}
}
