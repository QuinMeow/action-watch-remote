// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Real foreground GPS only. Never creates mock positions or requests a last-known cached fix. */
public final class GpsController implements LocationListener {
 public interface Listener {void changed();}
 private final LocationManager manager;
 private final Listener listener;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean active;
 private GpsData.Gga gga;
 private long ggaReceived,ggaUtcMillis;
 private GpsData.Fix fix;private Location latestLocation;private long lastProcessed=-1;
 private final GpsSourceRules.VerticalEstimator vertical=new GpsSourceRules.VerticalEstimator();
 private long previousSampleTime=-1;
 public String message="GPS已关闭";
 public double sampleHz;
 private final OnNmeaMessageListener nmea=(sentence,timestamp)->{
  if(!active)return;
  GpsData.Gga parsed=GpsData.parseGga(sentence);
  if(parsed!=null){gga=parsed;ggaUtcMillis=timestamp;ggaReceived=SystemClock.elapsedRealtime();if(latestLocation!=null)processLocation(latestLocation);}
  else if(sentence!=null&&(sentence.startsWith("$GPGGA,")||sentence.startsWith("$GNGGA,"))){gga=null;vertical.reset();reject("GGA无有效定位或校验失败，暂停推送");}
 };
 public GpsController(Context context,Listener listener){manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);this.listener=listener;}
 public boolean active(){return active;}
 public GpsData.Fix fix(){return fix;}
 public void start(){
  stop();
  if(manager==null){message="系统没有定位服务";listener.changed();return;}
  active=true;
  try{
   if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){stop();message="请在系统开启GPS定位";listener.changed();return;}
   if(!manager.addNmeaListener(nmea,handler)){stop();message="设备不提供NMEA，无法验证海拔/卫星数";listener.changed();return;}
   manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,100,0,this,Looper.getMainLooper());
   message="正在定位，请到开阔处";
  }catch(SecurityException e){stop();message="精确定位权限未授权";}catch(IllegalArgumentException e){stop();message="系统GPS不可用";}
  listener.changed();
 }
 public void stop(){active=false;fix=null;latestLocation=null;lastProcessed=-1;vertical.reset();gga=null;previousSampleTime=-1;sampleHz=0;if(manager!=null){try{manager.removeUpdates(this);manager.removeNmeaListener(nmea);}catch(SecurityException ignored){}}message="GPS已关闭";}
 private void reject(String reason){fix=null;message=reason;listener.changed();}
 @Override public void onLocationChanged(Location l){if(!active)return;
  long measured=l.getElapsedRealtimeNanos()/1000000L,age=SystemClock.elapsedRealtime()-measured;
  if(!l.isFromMockProvider()&&age>=0&&age<=2000&&measured>previousSampleTime){if(previousSampleTime>=0)sampleHz=1000.0/(measured-previousSampleTime);previousSampleTime=measured;}
  latestLocation=new Location(l);processLocation(latestLocation);
 }
 private void processLocation(Location l){
  if(!active)return;long now=SystemClock.elapsedRealtime();long measured=l.getElapsedRealtimeNanos()/1000000L;
  if(l.isFromMockProvider()){vertical.reset();reject("请关闭系统模拟定位");return;}
  if(measured==lastProcessed)return;
  if(now-measured>2000||now<measured){vertical.reset();reject("定位已过期，暂停推送");return;}
  
  if(gga==null||now-ggaReceived>2000||Math.abs(l.getTime()-ggaUtcMillis)>50||!GpsSourceRules.matches(gga,l.getTime(),l.getLongitude(),l.getLatitude(),l.hasAccuracy()?l.getAccuracy():Double.NaN)){reject("等待完整GPS数据");return;}
  if(!l.hasAccuracy()||!l.hasVerticalAccuracy()||!l.hasSpeed()||!l.hasSpeedAccuracy()||(l.getSpeed()>0&&!l.hasBearing())){reject("定位缺少精度/速度/方向字段，暂停推送");return;}
  double radians=Math.toRadians(l.hasBearing()?l.getBearing():0),north=l.getSpeed()*Math.cos(radians),east=l.getSpeed()*Math.sin(radians);
  lastProcessed=measured;
  double down=vertical.add(measured,gga.altitudeMslMeters);
  GpsData.Fix candidate=new GpsData.Fix(l.getTime(),measured,l.getLongitude(),l.getLatitude(),gga.altitudeMslMeters,north,east,down,l.getVerticalAccuracyMeters(),l.getAccuracy(),l.getSpeedAccuracyMetersPerSecond(),gga.satellites);
  if(!Double.isFinite(down)){reject("正在获取高度变化");return;}
  String invalid=GpsData.invalidReason(candidate,now);if(invalid!=null){reject("GPS字段无效："+invalid);return;}
  fix=candidate;message="GPS";listener.changed();
 }
 @Override public void onProviderDisabled(String provider){if(LocationManager.GPS_PROVIDER.equals(provider)){stop();message="GPS已关闭，推送已停止";listener.changed();}}
 @Override public void onProviderEnabled(String provider){}
 @Override public void onStatusChanged(String provider,int status,Bundle extras){}
}
