// SPDX-License-Identifier: MIT
package dev.actionwatch.remote;
public final class DebugGateTest {
 private static int checks;
 private static void check(boolean v){if(!v)throw new AssertionError("Debug gate check "+checks);checks++;}
 public static void main(String[] args){
  DebugGate g=new DebugGate();check(!g.enabled());
  for(int i=0;i<6;i++)check(!g.tap(i*100));
  check(g.tap(600));check(g.enabled());g.disable();check(!g.enabled());
  g.tap(1000);g.tap(4001);for(int i=0;i<5;i++)check(!g.tap(4100+i*100));check(g.tap(4600));
  g.disable();g.tap(1000);check(!g.tap(0));check(!g.enabled());
  System.out.println("ALL "+checks+" DEBUG GATE CHECKS PASSED");
 }
}
