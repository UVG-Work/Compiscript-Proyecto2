package com.uvg.sql.ide;

import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Arranque del IDE: {@code java -jar sql-interprete.jar --ide [--db dir] [script.sql]}. */
public final class IdeApp {

    private IdeApp() {
    }

    public static void launch(Path database, Path script) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // El look and feel por defecto de Swing sirve igual.
        }
        SwingUtilities.invokeLater(() -> {
            IdeFrame frame = new IdeFrame(database);
            frame.setVisible(true);
            if (script != null) {
                frame.open(script);
            }
        });
    }
}
