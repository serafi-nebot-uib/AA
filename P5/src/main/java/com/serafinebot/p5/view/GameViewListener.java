package com.serafinebot.p5.view;

/** Listener implemented by the controller to react to user-interface events. */
public interface GameViewListener {
    /** Called whenever a form field changes and the position must be solved again. */
    void configurationChanged();
}
