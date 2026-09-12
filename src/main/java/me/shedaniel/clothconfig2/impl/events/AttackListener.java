package me.shedaniel.clothconfig2.impl.events;

import java.util.ArrayList;

public interface AttackListener extends Listener {
   void onAttack(AttackListener.AttackEvent var1);

   public static class AttackEvent extends CancellableEvent<AttackListener> {
      @Override
      public void fire(ArrayList<AttackListener> listeners) {
         listeners.forEach(e -> e.onAttack(this));
      }

      @Override
      public Class<AttackListener> getListenerType() {
         return AttackListener.class;
      }
   }
}
