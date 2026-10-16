package dev.actionwatch.remote;
import java.util.*;
import java.nio.file.*;
import java.time.Instant;

/** Executes real production protocol/session/encoder against an explicitly fake camera. */
public class SimulationTest implements RemoteSession.Link {
 final RemoteSession session=new RemoteSession(this);final SimulatedCamera peer=new SimulatedCamera();final ArrayDeque<byte[]> incoming=new ArrayDeque<>();
 long now=1000;int count;String closed;final List<byte[]> sent=new ArrayList<>();
 public void send(byte[] b){sent.add(b);incoming.addAll(peer.accept(b,now));}
 public void changed(){}
 public void close(String reason){closed=reason;session.reset();}
 void flush(){while(!incoming.isEmpty())session.receive(incoming.remove(),now);}
 void advance(long ms){long end=now+ms;while(now<end){now=Math.min(now+100,end);flush();for(byte[] b:peer.tick(now))session.receive(b,now);session.tick(now);}}
 void check(boolean result,String label){if(!result)throw new AssertionError(label);count++;System.out.println("PASS SIM "+label);}
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02X ",x&255));return s.toString().trim();}
 static String nmea(String body){int c=0;for(char x:body.toCharArray())c^=x;return "$"+body+"*"+String.format(Locale.ROOT,"%02X",c);}
 void run() throws Exception {
  System.out.println("LOCAL JVM SIMULATION: fake camera + synthetic GPS only; no Android UI, BLE or real location.");
  session.start(new byte[10],1234,now);flush();advance(100);check(session.stage==RemoteSession.Stage.READY,"fake pairing through actual frame decoder");
  session.switchMode(5,now);byte[] modeFrame=sent.get(sent.size()-1);check(session.pendingMode&&session.mode==1,"mode remains old before ACK");flush();check(session.pendingMode&&session.mode==1,"successful mode ACK is not confirmation");advance(200);check(!session.pendingMode&&session.mode==5,"state push confirms photo mode");
  check(!session.canRecord(now),"photo mode does not enable record button");session.switchMode(1,now);int before=sent.size();session.switchMode(0,now);check(sent.size()==before,"duplicate mode requests blocked");check(!session.canRecord(now),"record blocked while mode pending");flush();advance(200);check(session.mode==1&&session.canRecord(now),"video mode confirmed");
  peer.nextFault=SimulatedCamera.Fault.REJECT;session.switchMode(0,now);flush();check(!session.pendingMode&&session.mode==1&&session.message.contains("拒绝"),"rejected mode does not change real mode");
  peer.nextFault=SimulatedCamera.Fault.ACK_ONLY;session.switchMode(0,now);flush();check(session.pendingMode,"ACK-only stays pending");advance(6000);check(!session.pendingMode&&session.message.contains("超时"),"ACK-only mode times out without false success");advance(500);check(session.mode==1,"fresh status restores actual old mode");
  peer.nextFault=SimulatedCamera.Fault.WRONG_SEQUENCE;session.switchMode(0,now);flush();check(session.pendingMode&&!session.message.contains("已应答"),"wrong-sequence mode ACK ignored");advance(6500);
  session.switchMode(1,now);check(!session.pendingMode,"same-mode no-op");session.switchMode(0x7f,now);check(!session.pendingMode,"unsupported mode no-op");
  session.toggle(now);flush();advance(200);check(session.recording(),"fake recording confirmed by state");check(!session.canSwitchMode(now),"mode change blocked during recording");session.toggle(now);flush();advance(200);check(!session.recording(),"fake stop confirmed by state");
  GpsData.Gate gate=new GpsData.Gate();int gpsBefore=peer.gpsFrames;GpsData.Fix fix=new GpsData.Fix(Instant.parse("2026-12-31T16:00:00Z").toEpochMilli(),now,116.391,39.907,50,1,0,-0.5,3,4,0.5,12);byte[] gpsFrame=null;
  for(int i=0;i<10;i++){byte[] p=gate.poll(fix,now,true,true);if(p!=null){check(session.sendGps(p,now),"GPS frame accepted in connected idle state "+i);gpsFrame=sent.get(sent.size()-1);}advance(100);}
  check(peer.gpsFrames-gpsBefore==10,"1Hz synthetic sample sends 10 transport frames in one second");
  int frames=peer.gpsFrames;byte[] p=gate.poll(fix,now,true,false);check(p==null&&peer.gpsFrames==frames,"congested GPS slot dropped, not queued");advance(2000);check(gate.poll(fix,now,true,true)==null,"stale synthetic fix not sent");
  peer.sendStatus=false;advance(3500);check(!session.fresh(now)&&!session.sendGps(new byte[48],now),"stale camera state blocks GPS");advance(8000);check(closed!=null&&session.stage==RemoteSession.Stage.OFF,"lost fake status disconnects");
  GpsSourceRules.VerticalEstimator vertical=new GpsSourceRules.VerticalEstimator();check(Double.isNaN(vertical.add(0,100)),"first vertical sample cannot fabricate zero");check(Double.isNaN(vertical.add(100,100.1)),"10Hz second sample waits for baseline");check(Math.abs(vertical.add(200,100.2)+1)<0.00001,"10Hz accumulated baseline calculates downward speed");check(Math.abs(vertical.add(300,100.3)+1)<0.00001,"10Hz next sample retains valid recent derivative");check(Math.abs(vertical.add(400,100.4)+1)<0.00001,"10Hz following baseline updates derivative");check(Double.isNaN(vertical.add(6000,105)),"long GPS gap resets vertical estimate");vertical.reset();check(Double.isNaN(vertical.add(7000,105)),"stopped GPS resets derivative");
  GpsData.Gga g=GpsData.parseGga(nmea("GPGGA,120000.100,3954.420,N,11623.460,E,1,12,0.8,50.0,M,0.0,M,,"));long time=Instant.parse("2026-10-16T12:00:00.100Z").toEpochMilli();check(GpsSourceRules.matches(g,time,116.391,39.907,4),"GGA matches fractional UTC and coordinates");check(!GpsSourceRules.matches(g,time+100,116.391,39.907,4),"different 10Hz sample in same second rejected");check(!GpsSourceRules.matches(g,time,117,40,4),"different GGA position rejected");
  Files.createDirectories(Paths.get("docs"));Files.write(Paths.get("docs/SIMULATED-FRAMES.txt"),Arrays.asList("# Synthetic test data only. MODE then GPS, for official checker.",hex(modeFrame),hex(gpsFrame)));
  System.out.println("ALL "+count+" SIMULATION CHECKS PASSED");
 }
 public static void main(String[] args)throws Exception{new SimulationTest().run();}
}
