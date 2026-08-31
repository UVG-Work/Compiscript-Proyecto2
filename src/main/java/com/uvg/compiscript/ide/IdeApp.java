package com.uvg.compiscript.ide;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada del IDE. Se lanza con {@code java -jar compiscript.jar --ide}. */
public final class IdeApp {

    private IdeApp() {
    }

    public static void launch(String file) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // El look and feel por defecto sirve igual.
        }
        SwingUtilities.invokeLater(() -> {
            IdeFrame frame = new IdeFrame();
            frame.setVisible(true);
            if (file != null) {
                frame.open(Path.of(file));
            }
        });
    }

    public static void main(String[] args) {
        launch(args.length > 0 ? args[0] : null);
    }
}
