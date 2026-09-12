package me.shedaniel.clothconfig2.impl.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;


public final class EventManager {
   private static final EventManager INSTANCE = new EventManager();
   private final HashMap<Class<? extends Listener>, ArrayList<EventManager.PrioritizedListener<? extends Listener>>> listenerMap = new HashMap<>();

   public static <L extends Listener, E extends Event<L>> void fire(E event) {
      INSTANCE.fireImpl(event);
   }

   public static <L extends Listener> void add(Class<L> type, L listener) {
      INSTANCE.addImpl(type, listener, 0);
   }

   public static <L extends Listener> void remove(Class<L> type, L listener) {
      INSTANCE.removeImpl(type, listener);
   }

   private <L extends Listener, E extends Event<L>> void fireImpl(E event) {
      Class<L> listenerType = event.getListenerType();
      @SuppressWarnings("unchecked")
      ArrayList<EventManager.PrioritizedListener<L>> listeners = (ArrayList<EventManager.PrioritizedListener<L>>)(ArrayList<?>)this.listenerMap.get(listenerType);
      if (listeners != null && !listeners.isEmpty()) {
         ArrayList<EventManager.PrioritizedListener<L>> listeners2 = new ArrayList<>(listeners);
         listeners2.removeIf(Objects::isNull);
         listeners2.sort(Comparator.comparing(listener -> Integer.MAX_VALUE - listener.getPriority()));
         ArrayList<L> listeners3 = new ArrayList<>();
         listeners2.forEach(listener -> listeners3.add(listener.getListener()));
         event.fire(listeners3);
      }
   }

   @SuppressWarnings("unchecked")
   private <L extends Listener> void addImpl(Class<L> type, L listener, int priority) {
      ArrayList<EventManager.PrioritizedListener<L>> listeners = (ArrayList<EventManager.PrioritizedListener<L>>)(ArrayList<?>)this.listenerMap.get(type);
      if (listeners == null) {
         listeners = new ArrayList<>();
         this.listenerMap.put(type, (ArrayList<EventManager.PrioritizedListener<? extends Listener>>)(ArrayList<?>)listeners);
      }
      listeners.add(new EventManager.PrioritizedListener<>(listener, priority));
   }

   @SuppressWarnings("unchecked")
   private <L extends Listener> void removeImpl(Class<L> type, L listener) {
      ArrayList<EventManager.PrioritizedListener<L>> listeners = (ArrayList<EventManager.PrioritizedListener<L>>)(ArrayList<?>)this.listenerMap.get(type);
      if (listeners != null) {
         listeners.removeIf(l -> l.getListener().equals(listener));
      }
   }

   private static class PrioritizedListener<L extends Listener> {
      private final L listener;
      private final int priority;

      public PrioritizedListener(L listener, int priority) {
         this.listener = listener;
         this.priority = priority;
      }

      public int getPriority() {
         return this.priority;
      }

      public L getListener() {
         return this.listener;
      }
   }
}
