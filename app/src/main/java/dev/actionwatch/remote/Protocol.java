// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;
import java.util.*;

/** Independently implemented R SDK framing. No DJI implementation code included. */
public final class Protocol {
 private Protocol() {}
 public static int u16(byte[] b,int o){return (b[o]&255)|((b[o+1]&255)<<8);}
 public static long u32(byte[] b,int o){return (u16(b,o)&65535L)|((long)u16(b,o+2)<<16);}
 public static void put(byte[] b,int o,long v,int n){for(int i=0;i<n;i++) b[o+i]=(byte)(v >>> (8*i));}
 public static int crc16(byte[] b,int len){int c=0x3aa3;for(int i=0;i<len;i++){c^=b[i]&255;for(int j=0;j<8;j++)c=(c>>>1)^((c&1)!=0?0xa001:0);}return c&65535;}
 public static long crc32(byte[] b,int len){long c=0x3aa3;for(int i=0;i<len;i++){c^=b[i]&255;for(int j=0;j<8;j++)c=(c>>>1)^((c&1)!=0?0xedb88320L:0);}return c&0xffffffffL;}
 public static byte[] frame(int set,int id,int type,int seq,byte[] payload){
  int n=18+payload.length;if(n>1023)throw new IllegalArgumentException("Frame too large");
  byte[] b=new byte[n];b[0]=(byte)0xaa;put(b,1,n,2);b[3]=(byte)type;put(b,8,seq,2);put(b,10,crc16(b,10),2);b[12]=(byte)set;b[13]=(byte)id;System.arraycopy(payload,0,b,14,payload.length);put(b,n-4,crc32(b,n-4),4);return b;
 }
 public static final class Frame {
  public final int type,seq,set,id;public final byte[] payload;
  Frame(byte[] b){type=b[3]&255;seq=u16(b,8);set=b[12]&255;id=b[13]&255;payload=Arrays.copyOfRange(b,14,b.length-4);}
  public boolean response(){return (type&0x20)!=0;}
 }
 public static final class Decoder {
  private byte[] pending=new byte[0];private long last;
  public void clear(){pending=new byte[0];last=0;}
  public List<Frame> feed(byte[] chunk,long now){
   if(now-last>2000)clear();last=now;
   if(chunk.length>4096){clear();return Collections.emptyList();}
   byte[] b=Arrays.copyOf(pending,pending.length+chunk.length);System.arraycopy(chunk,0,b,pending.length,chunk.length);
   List<Frame> out=new ArrayList<>();int p=0;
   while(b.length-p>=12){
    if((b[p]&255)!=0xaa){p++;continue;}
    int raw=u16(b,p+1), n=raw&1023;
    byte[] h=Arrays.copyOfRange(b,p,p+12);
    if(raw>>>10!=0||n<18||b[p+4]!=0||crc16(h,10)!=u16(h,10)){p++;continue;}
    if(b.length-p<n)break;
    byte[] f=Arrays.copyOfRange(b,p,p+n);
    if(crc32(f,n-4)!=u32(f,n-4)){p++;continue;}
    out.add(new Frame(f));p+=n;
   }
   pending=Arrays.copyOfRange(b,p,b.length);if(pending.length>1023)clear();return out;
  }
 }
 public static final class Sequence {private int value;public int next(){value=(value+1)&65535;return value;}}
 public static final class Pending {
  public final int seq,set,id;public final long deadline;
  public Pending(int seq,int set,int id,long deadline){this.seq=seq;this.set=set;this.id=id;this.deadline=deadline;}
  public boolean matches(Frame f){return f.response()&&f.seq==seq&&f.set==set&&f.id==id;}
  public boolean expired(long now){return now>=deadline;}
 }
}
