// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;

/** Deliberate, session-only discovery gesture; never a security boundary. */
public final class DebugGate {
 private int taps;
 private long lastTap=-1;
 private boolean enabled;
 public boolean tap(long now) {
  if(enabled)return true;
  if(lastTap<0||now<lastTap||now-lastTap>2000)taps=0;
  lastTap=now;
  if(++taps>=7){enabled=true;taps=0;}
  return enabled;
 }
 public boolean enabled(){return enabled;}
 public void disable(){enabled=false;taps=0;lastTap=-1;}
}
