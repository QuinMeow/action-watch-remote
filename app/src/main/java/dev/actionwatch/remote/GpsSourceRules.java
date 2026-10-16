// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;
/** Pure, testable matching/estimation rules shared by the actual Android GPS controller. */
public final class GpsSourceRules {
 private GpsSourceRules(){}
 public static boolean matches(GpsData.Gga g,long utcMillis,double longitude,double latitude,double accuracy){
  if(g==null||!Double.isFinite(longitude)||!Double.isFinite(latitude)||!Double.isFinite(accuracy)||accuracy<0)return false;
  long tod=Math.floorMod(utcMillis,86400000L),delta=Math.abs(tod-g.millisOfDay);delta=Math.min(delta,86400000L-delta);
  if(delta>50)return false;
  double lat1=Math.toRadians(latitude),lat2=Math.toRadians(g.latitudeDegrees);
  double dlat=lat2-lat1,dlon=Math.toRadians(g.longitudeDegrees-longitude);
  double a=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);
  double distance=6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
  return distance<=Math.min(100,Math.max(10,accuracy*2));
 }
 public static final class VerticalEstimator {
  private long base=-1,lastComputed=-1;private double altitude,lastDown=Double.NaN;
  public void reset(){base=lastComputed=-1;lastDown=Double.NaN;}
  public double add(long elapsedMs,double mslMeters){
   if(elapsedMs<0||!Double.isFinite(mslMeters)){reset();return Double.NaN;}
   if(base<0||elapsedMs<=base||elapsedMs-base>5000){base=elapsedMs;altitude=mslMeters;lastComputed=-1;lastDown=Double.NaN;return Double.NaN;}
   if(elapsedMs-base>=200){lastDown=(altitude-mslMeters)/((elapsedMs-base)/1000.0);lastComputed=elapsedMs;base=elapsedMs;altitude=mslMeters;}
   return lastComputed>=0&&elapsedMs-lastComputed<=1000?lastDown:Double.NaN;
  }
 }
}
