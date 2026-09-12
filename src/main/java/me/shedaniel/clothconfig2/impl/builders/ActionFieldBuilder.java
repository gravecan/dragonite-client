package me.shedaniel.clothconfig2.impl.builders;

public class ActionFieldBuilder extends AbstractFieldBuilder {
    private final Runnable action;

    public ActionFieldBuilder(String name, String description, Runnable action) {
        super(name, description);
        this.action = action;
    }

    public void run() {
        if (action != null) action.run();
    }
}
