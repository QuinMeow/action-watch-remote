// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;
import java.util.*;

/** Local deterministic fake protocol peer. No Bluetooth/network/location access. Not DJI software. */
public final class SimulatedCamera {
 public enum Fault { NONE, REJECT, ACK_ONLY, DROP, WRONG_SEQUENCE }
 public Fault nextFault=Fault.NONE;
 public boolean sendStatus=true;
 public int mode=1,state=1,gpsFrames=0;
 private int nextMode=-1,nextState=-1;
 private long applyAt,nextStatus;
 private boolean subscribed;
 private final Protocol.Decoder decoder=new Protocol.Decoder();
 public List<byte[]> accept(byte[] bytes,long now){
  List<byte[]> out=new ArrayList<>();
  for(Protocol.Frame f:decoder.feed(bytes,now)){
   if(f.response())continue;
   if(f.set==0&&f.id==0x19){
    byte[] ack=new byte[9];ack[0]=0x44;ack[1]=(byte)0xff;out.add(Protocol.frame(0,0x19,0x20,f.seq,ack));
    byte[] p=new byte[33];p[0]=0x44;p[1]=(byte)0xff;p[26]=2;out.add(Protocol.frame(0,0x19,2,900,p));
   }else if(f.set==0x1d&&f.id==5){subscribed=true;nextStatus=now;}
   else if(f.set==0&&f.id==0x17&&f.payload.length==48){gpsFrames++;}
   else if(f.set==0x1d&&(f.id==3||f.id==4)&&f.payload.length==9){
    Fault fault=nextFault;nextFault=Fault.NONE;
    if(fault==Fault.DROP)continue;
    out.add(Protocol.frame(f.set,f.id,0x20,f.seq+(fault==Fault.WRONG_SEQUENCE?1:0),new byte[]{(byte)(fault==Fault.REJECT?2:0),0,0,0,0}));
    if(fault==Fault.NONE){if(f.id==4)nextMode=f.payload[4]&255;else nextState=f.payload[4]==0?3:1;applyAt=now+200;}
   }
  }
  return out;
 }
 public List<byte[]> tick(long now){
  if(applyAt>0&&now>=applyAt){if(nextMode>=0)mode=nextMode;if(nextState>=0)state=nextState;nextMode=nextState=-1;applyAt=0;nextStatus=now;}
  if(!subscribed||!sendStatus||now<nextStatus)return Collections.emptyList();nextStatus=now+500;
  byte[] p=new byte[38];p[0]=(byte)mode;p[1]=(byte)state;p[37]=80;return Collections.singletonList(Protocol.frame(0x1d,2,0,901,p));
 }
}
