// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;
import java.util.*;

/** All methods are called from the main thread. Recording truth comes only from fresh camera status. */
public final class RemoteSession {
 public interface Link {void send(byte[] bytes);void changed();void close(String reason);}
 public enum Stage { OFF, PAIRING, STATUS, READY }
 public Stage stage=Stage.OFF;
 public String message="未连接";
 public int mode=-1,cameraStatus=-1,battery=-1,seconds=0,temperature=0,powerMode=-1;
 public boolean pendingRecord=false;
 public boolean pendingMode=false;private int desiredMode=-1;
 private Boolean desired;
 private final Link link;
 private final Protocol.Decoder decoder=new Protocol.Decoder();
 private final Protocol.Sequence sequence=new Protocol.Sequence();
 private Protocol.Pending pairing,record,modeRequest;
 private byte[] ownId=new byte[4],cameraId;
 private long stageDeadline,lastStatus;
 public RemoteSession(Link link){this.link=link;}
 public void start(byte[] identity,int code,long now){
  reset();stage=Stage.PAIRING;stageDeadline=now+45000;
  ownId=Arrays.copyOf(identity,4);
  byte[] p=new byte[33];System.arraycopy(ownId,0,p,0,4);p[4]=6;System.arraycopy(identity,4,p,5,6);p[26]=1;Protocol.put(p,27,code,2);
  int seq=sequence.next();pairing=new Protocol.Pending(seq,0,0x19,stageDeadline);
  message=String.format(Locale.ROOT,"请在相机确认配对码 %04d",code);
  link.send(Protocol.frame(0,0x19,2,seq,p));link.changed();
 }
 public void reset(){stage=Stage.OFF;decoder.clear();pairing=null;record=null;modeRequest=null;pendingMode=false;desiredMode=-1;pendingRecord=false;desired=null;cameraId=null;mode=-1;cameraStatus=-1;battery=-1;seconds=0;temperature=0;powerMode=-1;lastStatus=0;}
 public boolean fresh(long now){return stage==Stage.READY&&now-lastStatus<3500;}
 public boolean recording(){return cameraStatus==3&&videoMode();}
 public boolean videoMode(){return mode==0||mode==1||mode==2||mode==0x0a||mode==0x28;}
 public boolean canRecord(long now){return fresh(now)&&powerMode==0&&!pendingRecord&&!pendingMode&&videoMode()&&(cameraStatus==1||cameraStatus==3||cameraStatus==5)&&(recording()||temperature<2);}
 public void toggle(long now){
  if(!canRecord(now))return;
  desired=!recording();pendingRecord=true;
  byte[] p=new byte[9];System.arraycopy(cameraId,0,p,0,4);p[4]=(byte)(desired?0:1);
  int seq=sequence.next();record=new Protocol.Pending(seq,0x1d,3,now+6000);
  message=desired?"正在开始录像…":"正在停止录像…";
  link.send(Protocol.frame(0x1d,3,1,seq,p));link.changed();
 }
 public static String modeName(int value){switch(value){case 0:return "慢动作";case 1:return "普通录像";case 2:return "延时摄影";case 5:return "拍照";case 10:return "运动延时";case 40:return "低光视频";default:return value<0?"等待同步":"其他模式";}}
 public static boolean supportedMode(int value){return value==0||value==1||value==2||value==5||value==10||value==40;}
 public boolean canSwitchMode(long now){return fresh(now)&&powerMode==0&&!pendingRecord&&!pendingMode&&cameraStatus==1;}
 public void switchMode(int target,long now){
  if(!canSwitchMode(now)||!supportedMode(target)||target==mode)return;
  pendingMode=true;desiredMode=target;
  byte[] p=new byte[9];System.arraycopy(cameraId,0,p,0,4);p[4]=(byte)target;
  int seq=sequence.next();modeRequest=new Protocol.Pending(seq,0x1d,4,now+6000);
  message="切换至"+modeName(target)+"…";
  link.send(Protocol.frame(0x1d,4,1,seq,p));link.changed();
 }
 /** GPS has no camera ACK; return means queued locally, never proof of camera storage. */
 public boolean sendGps(byte[] payload,long now){
  if(payload==null||payload.length!=48||!fresh(now)||powerMode!=0||pendingRecord||pendingMode)return false;
  link.send(Protocol.frame(0,0x17,0,sequence.next(),payload));return true;
 }
 public void receive(byte[] data,long now){for(Protocol.Frame f:decoder.feed(data,now))handle(f,now);}
 private void handle(Protocol.Frame f,long now){
  byte[] p=f.payload;
  if(stage==Stage.PAIRING&&f.set==0&&f.id==0x19){
   if(f.response()){
    if(pairing!=null&&pairing.matches(f)&&p.length>=5&&p[4]!=0)link.close("相机拒绝连接请求："+(p[4]&255));
    return;
   }
   if(p.length<33||p[26]!=2)return;
   if(Protocol.u16(p,27)!=0){link.close("相机拒绝配对；请手动重新扫描");return;}
   cameraId=Arrays.copyOf(p,4);
   byte[] ack=new byte[9];System.arraycopy(ownId,0,ack,0,4);
   link.send(Protocol.frame(0,0x19,0x20,f.seq,ack));
   stage=Stage.STATUS;stageDeadline=now+8000;message="已获配对许可，等待相机状态";
   link.send(Protocol.frame(0x1d,5,0,sequence.next(),new byte[]{3,20,0,0,0,0}));link.changed();return;
  }
  if(stage!=Stage.STATUS&&stage!=Stage.READY)return;
  if(f.set==0x1d&&f.id==2&&!f.response()&&p.length>=38){
   mode=p[0]&255;cameraStatus=p[1]&255;seconds=Protocol.u16(p,5);powerMode=p[28]&255;temperature=p[30]&255;battery=p[37]&255;lastStatus=now;stage=Stage.READY;
   if(pendingRecord&&desired!=null&&recording()==desired){pendingRecord=false;record=null;desired=null;}
   if(pendingMode&&mode==desiredMode){pendingMode=false;modeRequest=null;desiredMode=-1;}
   if(!pendingRecord&&!pendingMode) message=recording()?"录像中":(videoMode()?"已连接":"已连接");
   link.changed();
  } else if(modeRequest!=null&&modeRequest.matches(f)&&p.length>=1){
   if(p[0]!=0){message="相机拒绝模式切换："+(p[0]&255);pendingMode=false;modeRequest=null;desiredMode=-1;}
   else message="等待模式确认…";
   link.changed();
  } else if(record!=null&&record.matches(f)&&p.length>=1){
   if(p[0]!=0){message="相机拒绝录像命令："+(p[0]&255);pendingRecord=false;record=null;desired=null;}
   else message="等待录像确认…";
   link.changed();
  }
 }
 public void tick(long now){
  if((stage==Stage.PAIRING||stage==Stage.STATUS)&&now>=stageDeadline){link.close(stage==Stage.PAIRING?"配对超时，请检查相机屏幕":"未收到相机状态，请断开后重试");return;}
  if(modeRequest!=null&&modeRequest.expired(now)){modeRequest=null;pendingMode=false;desiredMode=-1;message="模式切换超时：结果未知，请查看相机";lastStatus=0;stage=Stage.STATUS;stageDeadline=now+8000;link.changed();}
  if(record!=null&&record.expired(now)){record=null;pendingRecord=false;desired=null;message="命令超时：结果未知，请查看相机";lastStatus=0;stage=Stage.STATUS;stageDeadline=now+8000;link.changed();}
  if(stage==Stage.READY&&!fresh(now)){stage=Stage.STATUS;stageDeadline=now+8000;message="状态过期，等待相机更新";link.changed();}
 }
}
