package me.shedaniel.clothconfig2.impl.events;

public abstract class CancellableEvent<T extends Listener> extends Event<T> {
   private boolean isCancelled = false;

   public boolean isCancelled() {
      return this.isCancelled;
   }

   public void cancel() {
      this.isCancelled = true;
   }
}
