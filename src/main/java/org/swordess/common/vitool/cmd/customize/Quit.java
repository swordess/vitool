package org.swordess.common.vitool.cmd.customize;

import org.springframework.shell.ExitRequest;
import org.springframework.shell.context.InteractionMode;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ShellComponent
public class Quit implements org.springframework.shell.standard.commands.Quit.Command {

    private static final List<Consumer<Void>> HANDLERS = new ArrayList<>();

    @ShellMethod(value = "Exit the shell.", key = {"quit", "exit"}, interactionMode = InteractionMode.INTERACTIVE,
            // place into the built-in group
            group = "Built-In Commands")
    public void quit() {
        HANDLERS.forEach(it -> it.accept(null));
        throw new ExitRequest();
    }

    public static void onExit(Consumer<Void> handler) {
        HANDLERS.add(handler);
    }

}
