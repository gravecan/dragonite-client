package me.shedaniel.clothconfig2.impl.events;

import java.util.ArrayList;

public abstract class Event<T extends Listener> {
   public abstract void fire(ArrayList<T> var1);

   public abstract Class<T> getListenerType();
}
