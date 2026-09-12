package me.shedaniel.clothconfig2.impl.utils;


public final class TimerUtils {
   private long lastMS;

   public TimerUtils() {
      this.reset();
   }

   public long getCurrentMS() {
      return System.nanoTime() / 1000000L;
   }

   public boolean hasReached(double milliseconds) {
      return this.getCurrentMS() - this.lastMS >= milliseconds;
   }

   public void reset() {
      this.lastMS = this.getCurrentMS();
   }

   public boolean delay(float milliSec) {
      return (float)(this.getTime() - this.lastMS) >= milliSec;
   }

   public long getTime() {
      return System.nanoTime() / 1000000L;
   }
}
