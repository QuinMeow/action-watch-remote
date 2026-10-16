// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.text.TextUtils;
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
 private TextView status,details,gpsDetails,cameraTitle,notice;
 private Button scan,record,gpsButton;private TextView modeButton;private LinearLayout modeArea;
 private LinearLayout root,recordDock;private ScrollView scroll;private String page="home";
 private static final int AMBER=0xffffca7a,INK=0xff261800,SURFACE=0xff20201d,MUTED=0xffbbb8b2;
 private String deviceName="Action 遥控";
 private final ArrayList<Button> modeChoices=new ArrayList<>();
 private AlertDialog activeDialog;
 private final DebugGate debugGate=new DebugGate();
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
  gps=new GpsController(this,()->render());
  showPage("home");
 }
 private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(0xfff7f5f1);v.setFontFeatureSettings("tnum");return v;}
 private Drawable icon(int resource,int color,int size){Drawable d=getDrawable(resource).mutate();d.setTint(color);d.setBounds(0,0,dp(size),dp(size));return d;}
 private void centerRecordContent(){if(record==null||record.getWidth()==0)return;int padding=Math.max(dp(8),(record.getWidth()-Math.round(record.getPaint().measureText(record.getText().toString()))-dp(36))/2);if(record.getPaddingLeft()!=padding)record.setPadding(padding,dp(8),padding,dp(8));}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(16);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{0xfff7f5f1,0xff777773}));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape(SURFACE,28),null));return b;}
 private void add(View v,int height,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));lp.topMargin=dp(top);root.addView(v,lp);}
 private void showPage(String name){
  if(!name.equals("home")&&scanning){stopScan();seen.clear();session.message="扫描已暂停";}
  page=name;recordDock=null;notice=null;status=null;details=null;gpsDetails=null;cameraTitle=null;scan=null;record=null;modeButton=null;modeArea=null;gpsButton=null;modeChoices.clear();
  scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.BLACK);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(8),dp(18),dp(16));scroll.addView(root);
  if(name.equals("home"))buildHome();
  else{
   Button back=button("返回");back.setContentDescription("返回主页");add(back,48,0);back.setOnClickListener(v->showPage(name.equals("about")||name.equals("debug")?"settings":"home"));
   TextView title=text(name.equals("settings")?"设置":name.equals("about")?"关于":name.equals("modes")?"选择模式":name.equals("gps")?"GPS":"Debug",22);title.setTypeface(null,Typeface.BOLD);add(title,-2,12);
   if(name.equals("settings"))buildSettings();
   else if(name.equals("about"))buildAbout();
   else if(name.equals("modes"))buildModes();
   else if(name.equals("gps")){gpsDetails=text("GPS已关闭",15);add(gpsDetails,-2,16);gpsButton=button("开启GPS");add(gpsButton,56,16);gpsButton.setOnClickListener(v->toggleGps());}
   else buildDebug();
  }
  View content=scroll;
  if(name.equals("home")&&getResources().getConfiguration().screenHeightDp<290){
   root.removeView(record);
   LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(Color.BLACK);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
   recordDock=new LinearLayout(this);recordDock.setPadding(dp(18),0,dp(18),dp(16));recordDock.addView(record,new LinearLayout.LayoutParams(-1,dp(88)));outer.addView(recordDock,new LinearLayout.LayoutParams(-1,-2));content=outer;
  }
  content.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  setContentView(content);content.requestApplyInsets();render();
 }
 private void buildHome(){
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
  cameraTitle=text(deviceName,16);cameraTitle.setSingleLine();cameraTitle.setEllipsize(TextUtils.TruncateAt.END);cameraTitle.setTypeface(null,Typeface.BOLD);labels.addView(cameraTitle);
  status=text("未连接",12);status.setTextColor(MUTED);labels.addView(status);
  header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton settings=new ImageButton(this);settings.setContentDescription("设置");settings.setImageDrawable(icon(R.drawable.ic_settings,0xfff7f5f1,24));settings.setPadding(dp(13),dp(13),dp(13),dp(13));settings.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape(SURFACE,24),null));header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));settings.setOnClickListener(v->showPage("settings"));add(header,48,0);
  notice=text("",14);notice.setTextColor(MUTED);add(notice,-2,8);
  modeArea=new LinearLayout(this);modeArea.setOrientation(LinearLayout.VERTICAL);modeArea.setGravity(Gravity.CENTER);modeArea.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffca7a),shape(Color.BLACK,24),null));modeArea.setOnClickListener(v->chooseMode());modeArea.setContentDescription("切换模式");
  details=text("普通录像",25);details.setTypeface(null,Typeface.BOLD);details.setGravity(Gravity.CENTER);details.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);modeArea.addView(details);
  modeButton=text("切换模式",16);modeButton.setTextColor(AMBER);modeButton.setGravity(Gravity.CENTER);modeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);modeButton.setCompoundDrawables(null,null,icon(R.drawable.ic_chevron_right,AMBER,18),null);modeButton.setCompoundDrawablePadding(dp(2));modeArea.addView(modeButton,new LinearLayout.LayoutParams(-2,-2));add(modeArea,64,8);
  gpsButton=button("GPS 已关闭");gpsButton.setCompoundDrawables(icon(R.drawable.ic_location_on,MUTED,20),null,icon(R.drawable.ic_chevron_right,MUTED,16),null);gpsButton.setCompoundDrawablePadding(dp(4));gpsButton.setSingleLine(true);gpsButton.setEllipsize(TextUtils.TruncateAt.END);add(gpsButton,48,8);gpsButton.setOnClickListener(v->showPage("gps"));
  record=button("开始录像");record.setTextSize(20);record.setTypeface(null,Typeface.BOLD);record.setTextColor(INK);record.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(AMBER,44),null));record.setCompoundDrawables(icon(R.drawable.ic_radio_button_checked,INK,24),null,null,null);record.setCompoundDrawablePadding(dp(8));record.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->centerRecordContent());add(record,88,12);record.setOnClickListener(v->session.toggle(now()));
  scan=button("连接相机");scan.setTextColor(INK);scan.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000),shape(AMBER,32),null));add(scan,64,20);scan.setOnClickListener(v->requestScan());
  devices=new LinearLayout(this);devices.setOrientation(LinearLayout.VERTICAL);add(devices,-2,8);
 }
 private void buildSettings(){
  Button connection=button(gatt!=null||fake!=null||connecting||scanning?"断开连接":"连接相机");add(connection,56,12);connection.setOnClickListener(v->{if(gatt!=null||fake!=null||connecting||scanning){close("已断开");showPage("home");}else{showPage("home");requestScan();}});
  Button about=button("关于");add(about,48,8);about.setOnClickListener(v->showPage("about"));
  if(debugGate.enabled()){Button debug=button("Debug");add(debug,48,8);debug.setOnClickListener(v->showPage("debug"));}
 }
 private void buildAbout(){
  add(text("Action 手表遥控",18),-2,16);
  Button version=button("版本 0.3");add(version,56,12);version.setOnClickListener(v->{if(debugGate.tap(now())){Toast.makeText(this,"调试菜单已开启",Toast.LENGTH_SHORT).show();showPage("settings");}});
  TextView note=text("退出应用会断开连接并停止GPS。正在录制的视频请在相机上结束。",14);note.setTextColor(MUTED);add(note,-2,16);
 }
 private void buildModes(){
  int[] values={1,0,2,5,10,40};
  for(int value:values){Button b=button(RemoteSession.modeName(value));b.setTag(value);modeChoices.add(b);add(b,56,8);b.setOnClickListener(v->{if(session.canSwitchMode(now())){session.switchMode(value,now());showPage("home");}});}
 }
 private void buildDebug(){
  Button connect=button(fake==null?"连接模拟设备":"退出模拟");connect.setEnabled(fake!=null||(gatt==null&&!scanning&&!connecting));add(connect,56,12);connect.setOnClickListener(v->{if(fake==null)startSimulation();else close("已断开");showPage("home");});
  if(fake!=null){Button faults=button("模拟测试");add(faults,48,8);faults.setOnClickListener(v->simulationOptions());}
  gpsDetails=text("",14);add(gpsDetails,-2,12);
  Button disable=button("关闭Debug");add(disable,48,12);disable.setOnClickListener(v->{debugGate.disable();if(fake!=null)close("已断开");showPage("settings");});
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private boolean permissions(){if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 private void requestScan(){
  if(!page.equals("home"))showPage("home");
  if(!permissions()){requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7);return;}
  try{if(adapter==null){session.message="设备不支持蓝牙";render();return;}if(!adapter.isEnabled()){session.message="请在手表设置开启蓝牙";render();return;}
   close("扫描中…");devices.removeAllViews();seen.clear();scanner=adapter.getBluetoothLeScanner();if(scanner==null){close("蓝牙扫描不可用");return;}
   scanning=true;scan.setEnabled(false);scanCallback=newScanCallback(generation);scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
   int token=generation;h.postDelayed(()->{if(token!=generation||!scanning)return;stopScan();session.message=seen.isEmpty()?"未发现设备：检查相机蓝牙、定位开关和权限":"扫描结束，请选择你的相机";render();},12000);
  }catch(SecurityException e){close("缺少蓝牙/定位权限，请在设置允许");}catch(RuntimeException e){close("扫描失败："+e.getClass().getSimpleName());}
 }
 @Override public void onRequestPermissionsResult(int code,String[] ps,int[] results){super.onRequestPermissionsResult(code,ps,results);if(code==8){if(gpsPermissionGeneration==generation&&fake==null&&session.fresh(now())){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)gps.start();else gps.message="未获精确定位，GPS保持关闭";}gpsPermissionGeneration=-1;render();return;}if(code==7){if(permissions()){if(page.equals("home"))requestScan();else{session.message="权限已允许，请连接相机";render();}}else{session.message="权限未允许；可再次扫描或在设置中授权";render();}}}
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
  try{String n=d.getName();deviceName=n==null?"Action 相机":n;gatt=d.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);if(gatt==null)close("无法创建蓝牙连接");}catch(SecurityException e){close("蓝牙权限不足");}render();
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
 @Override public void close(String reason){if(activeDialog!=null)activeDialog.dismiss();generation++;gpsGate.reset();gpsPermissionGeneration=-1;if(gps!=null)gps.stop();fake=null;simulationGps=false;freezeSimulationGps=false;fakeFix=null;gpsSent=0;stopScan();connecting=false;writing=false;writes.clear();mtu=23;tx=null;BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();}catch(SecurityException ignored){}try{old.close();}catch(SecurityException ignored){}}session.reset();deviceName="Action 遥控";session.message=reason;render();}
 @Override public void changed(){render();}
 private void render(){
  boolean hasTransport=gatt!=null||fake!=null||connecting;
  boolean gpsOn=simulationGps||(gps!=null&&gps.active());
  GpsData.Fix f=fake!=null?fakeFix:gps==null?null:gps.fix();boolean valid=GpsData.invalidReason(f,now())==null;
  String gpsState=gpsOn?(valid?"定位正常":"正在定位"):(gps==null?"已关闭":gps.message);
  if(status!=null){boolean longMessage=session.message.length()>14;notice.setVisibility(longMessage?View.VISIBLE:View.GONE);notice.setText(longMessage?session.message:"");status.setText((fake!=null?"模拟 · ":"")+(longMessage?(session.fresh(now())?"已连接":hasTransport?"连接中":"未连接"):session.message));status.setTextColor(fake!=null?AMBER:session.fresh(now())?0xffa6e7c4:MUTED);cameraTitle.setText(deviceName);}
  if(details!=null){details.setVisibility(hasTransport?View.VISIBLE:View.GONE);details.setText(session.recording()?String.format(Locale.ROOT,"%02d:%02d",session.seconds/60,session.seconds%60):RemoteSession.modeName(session.mode));}
  if(recordDock!=null)recordDock.setVisibility(hasTransport?View.VISIBLE:View.GONE);
  if(record!=null){record.setVisibility(hasTransport?View.VISIBLE:View.GONE);record.setEnabled(session.canRecord(now()));record.setAlpha(record.isEnabled()?1f:0.5f);record.setText(session.pendingRecord?(session.recording()?"正在停止…":"正在开始…"):session.recording()?"停止录像":"开始录像");record.setCompoundDrawables(icon(session.recording()?R.drawable.ic_stop:R.drawable.ic_radio_button_checked,INK,28),null,null,null);centerRecordContent();}
  if(modeArea!=null){modeArea.setVisibility(hasTransport?View.VISIBLE:View.GONE);modeArea.setEnabled(session.canSwitchMode(now()));modeArea.setContentDescription(RemoteSession.modeName(session.mode)+"，切换模式");}
  if(modeButton!=null){modeButton.setVisibility(hasTransport?View.VISIBLE:View.GONE);modeButton.setEnabled(session.canSwitchMode(now()));modeButton.setAlpha(modeButton.isEnabled()?1f:0.5f);modeButton.setText(session.pendingMode?"正在切换…":"切换模式");}
  if(scan!=null){scan.setVisibility(hasTransport?View.GONE:View.VISIBLE);scan.setEnabled(!scanning);scan.setText(scanning?"扫描中…":"连接相机");}
  if(gpsButton!=null){
   if(page.equals("home")){gpsButton.setVisibility(hasTransport?View.VISIBLE:View.GONE);gpsButton.setText(gpsOn?(valid?"GPS正常":"GPS定位"):"GPS关闭");gpsButton.setContentDescription("GPS，"+(gpsOn?gpsState:"已关闭"));}
   else{gpsButton.setEnabled(gpsOn||session.fresh(now()));gpsButton.setText(gpsOn?"关闭GPS":fake!=null?"开启模拟GPS":"开启GPS");}
  }
  if(gpsDetails!=null){
   if(page.equals("debug"))gpsDetails.setText(String.format(Locale.CHINA,"采样 %.1f Hz\n发送上限 10 Hz\n已发送 %d 帧",fake!=null?1.0:gps.sampleHz,gpsSent));
   else gpsDetails.setText((fake!=null?"模拟GPS · ":"")+(fake==null&&gpsOn&&!valid?(f!=null?"定位已过期，等待更新":gps.message):gpsState));
  }
  for(Button b:modeChoices){int mode=(Integer)b.getTag();b.setEnabled(session.canSwitchMode(now()));b.setText(RemoteSession.modeName(mode)+(session.mode==mode?" · 当前":""));b.setTextColor(session.mode==mode?AMBER:0xfff7f5f1);}
 }
 private void showDialog(AlertDialog.Builder builder){if(activeDialog!=null)activeDialog.dismiss();activeDialog=builder.create();activeDialog.setOnDismissListener(d->{if(activeDialog==d)activeDialog=null;});activeDialog.show();}
 private void chooseMode(){if(session.canSwitchMode(now()))showPage("modes");}
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
  if(!debugGate.enabled()||fake!=null||gatt!=null||scanning||connecting)return;
  close("启动本机模拟");deviceName="Action 5 Pro";fake=new SimulatedCamera();session.start(new byte[10],1234,now());render();
 }
 private void simulationOptions(){
  if(!debugGate.enabled()||fake==null)return;final SimulatedCamera peer=fake;
  String[] options={"恢复正常响应","下条指令：拒绝","下条指令：仅应答","下条指令：丢包","下条指令：错序应答","暂停/恢复状态","暂停/恢复GPS","模拟断连"};
  showDialog(new AlertDialog.Builder(this).setTitle("模拟测试").setItems(options,(d,index)->{if(fake!=peer)return;if(index<5)peer.nextFault=SimulatedCamera.Fault.values()[index];else if(index==5)peer.sendStatus=!peer.sendStatus;else if(index==6)freezeSimulationGps=!freezeSimulationGps;else close("模拟设备已断开");render();}).setNegativeButton("取消",null));
 }
 @Override protected void onStart(){super.onStart();h.removeCallbacks(ticker);h.removeCallbacks(gpsTicker);h.post(ticker);h.post(gpsTicker);}
 @Override protected void onStop(){super.onStop();h.removeCallbacks(ticker);h.removeCallbacks(gpsTicker);debugGate.disable();close("已断开，请重新连接");showPage("home");}
 @Override public void onBackPressed(){if(!page.equals("home"))showPage(page.equals("about")||page.equals("debug")?"settings":"home");else super.onBackPressed();}
 @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy();}
}
