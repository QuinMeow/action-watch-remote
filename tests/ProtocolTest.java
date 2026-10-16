package dev.actionwatch.remote;
import java.util.*;
public class ProtocolTest {
 static int count;
 static void check(boolean v,String label){if(!v)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
 static byte[] hex(String s){s=s.replace(" ","");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 static class Fake implements RemoteSession.Link {List<byte[]> sent=new ArrayList<>();String closed;RemoteSession s;public void send(byte[] b){sent.add(b);}public void changed(){}public void close(String r){closed=r;s.reset();}}
 static byte[] status(int mode,int state){byte[] p=new byte[38];p[0]=(byte)mode;p[1]=(byte)state;p[37]=80;return Protocol.frame(0x1d,2,0,77,p);}
 static void pair(Fake f,long now){f.s.start(new byte[10],1234,now);byte[] p=new byte[33];p[0]=0x44;p[1]=(byte)0xff;p[26]=2;f.s.receive(Protocol.frame(0,0x19,2,19,p),now+1);f.s.receive(status(1,1),now+2);}
 public static void main(String[] args){
  // Published DJI documentation's complete fixed frame. Source linked in docs/PROTOCOL.md.
  byte[] fixture=hex("AA 1B 00 01 00 00 00 00 05 00 57 EE 1D 04 00 00 33 FF 0A 01 47 39 36 F4 FA E1 D0");
  check(Protocol.crc16(fixture,10)==0xee57,"official fixed CRC16 vector");
  check(Protocol.crc32(fixture,23)==0xd0e1faf4L,"official fixed CRC32 vector");
  check(Arrays.equals(fixture,Protocol.frame(0x1d,4,1,5,Arrays.copyOfRange(fixture,14,23))),"fixed frame byte exact");
  Protocol.Decoder d=new Protocol.Decoder();List<Protocol.Frame> fs=new ArrayList<>();for(byte b:fixture)fs.addAll(d.feed(new byte[]{b},100));check(fs.size()==1&&fs.get(0).seq==5,"bytewise fragmentation");
  byte[] twice=new byte[fixture.length*2];System.arraycopy(fixture,0,twice,0,fixture.length);System.arraycopy(fixture,0,twice,fixture.length,fixture.length);check(d.feed(twice,101).size()==2,"coalesced frames");
  byte[] bad=fixture.clone();bad[16]^=1;check(d.feed(bad,102).isEmpty(),"corrupt payload rejected");check(d.feed(fixture,103).size()==1,"resync after corrupt payload");
  bad=fixture.clone();bad[10]^=1;check(d.feed(bad,104).isEmpty(),"corrupt header rejected");check(d.feed(fixture,105).size()==1,"resync after corrupt header");
  d.clear();d.feed(Arrays.copyOf(fixture,15),100);check(d.feed(Arrays.copyOfRange(fixture,15,fixture.length),2201).isEmpty(),"partial frame timeout");check(d.feed(fixture,2202).size()==1,"recovery after timeout");
  Protocol.Sequence seq=new Protocol.Sequence();int x=0;for(int i=0;i<65536;i++)x=seq.next();check(x==0&&seq.next()==1,"sequence wraps uint16");
  Protocol.Pending pending=new Protocol.Pending(5,0x1d,4,300);Protocol.Frame ack=new Protocol.Decoder().feed(Protocol.frame(0x1d,4,0x20,5,new byte[1]),1).get(0);check(pending.matches(ack),"ACK matches sequence and command");check(!pending.matches(fs.get(0)),"command is not ACK");check(!pending.expired(299)&&pending.expired(300),"exact timeout deadline");
  Fake f=new Fake();f.s=new RemoteSession(f);pair(f,1000);check(f.s.stage==RemoteSession.Stage.READY&&f.sent.size()==3,"pair handshake and subscription");
  byte[] response=f.sent.get(1);check(Protocol.u16(response,8)==19&&response[3]==0x20,"pair response echoes camera sequence");
  f.s.toggle(1100);int n=f.sent.size();f.s.toggle(1101);check(f.sent.size()==n&&f.s.pendingRecord,"duplicate click blocked");
  byte[] cmd=f.sent.get(n-1);check(cmd[14]==0x44&&cmd[15]==(byte)0xff&&cmd[18]==0,"record uses raw camera ID and start=0");
  f.s.receive(Protocol.frame(0x1d,3,0x20,999,new byte[]{1}),1200);check(f.s.pendingRecord,"wrong-sequence ACK ignored");
  f.s.receive(Protocol.frame(0x1d,3,0x20,Protocol.u16(cmd,8),new byte[]{0}),1201);check(!f.s.recording()&&f.s.pendingRecord,"ACK alone does not claim recording");
  f.s.receive(status(1,3),1300);check(f.s.recording()&&!f.s.pendingRecord,"status confirms recording");
  f.s.toggle(1400);check(f.sent.get(f.sent.size()-1)[18]==1,"stop byte is one");f.s.tick(7400);check(!f.s.canRecord(7400)&&f.s.message.contains("未知"),"record timeout disables uncertain control");
  f.s.receive(status(1,1),7500);check(f.s.canRecord(7500),"fresh status recovers timeout");f.s.receive(status(5,3),7600);check(!f.s.recording()&&!f.s.canRecord(7600),"photo activity not labelled recording");
  f.s.tick(12000);check(!f.s.canRecord(12000),"stale status disables buttons");
  f.s.start(new byte[10],5678,20000);byte[] reject=new byte[33];reject[26]=2;reject[27]=1;f.s.receive(Protocol.frame(0,0x19,2,9,reject),20001);check(f.closed!=null&&f.s.stage==RemoteSession.Stage.OFF,"camera rejection disconnects");
  f.closed=null;f.s.start(new byte[10],1111,30000);f.s.tick(75000);check(f.closed!=null,"pairing timeout disconnects");
  f.closed=null;pair(f,80000);f.s.toggle(80100);byte[] latest=f.sent.get(f.sent.size()-1);f.s.receive(Protocol.frame(0x1d,3,0x20,Protocol.u16(latest,8),new byte[]{2}),80101);check(!f.s.pendingRecord&&!f.s.recording()&&f.s.message.contains("拒绝"),"record failure ACK clears pending without success");
  f.s.reset();check(f.s.battery==-1&&f.s.seconds==0&&f.s.temperature==0,"reset clears stale display values");
  d.clear();java.util.Random rng=new java.util.Random(42);for(int i=0;i<1000;i++){byte[] noise=new byte[31];rng.nextBytes(noise);d.feed(noise,i);}check(d.feed(fixture,5000).size()==1,"bounded random noise recovers to valid frame");
  System.out.println("ALL "+count+" CHECKS PASSED");
 }
}
