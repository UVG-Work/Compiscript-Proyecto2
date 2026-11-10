package com.uvg.compiscript.ide;

import com.uvg.compiscript.compiler.CompilationResult;
import com.uvg.compiscript.compiler.CompilerService;
import com.uvg.compiscript.mips.MarsRunner;
import com.uvg.compiscript.semantic.SemanticError;
import com.uvg.compiscript.tools.TestRunner;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ventana del IDE: editor a la izquierda, arbol / tabla de simbolos / tokens a
 * la derecha, y diagnosticos y salida abajo. Cubre el "Funcionamiento del
 * programa" del enunciado: compilar hasta MIPS, ejecutarlo en MARS y correr la
 * bateria de pruebas.
 */
public class IdeFrame extends JFrame {

    private static final String PLANTILLA = """
            // Programa nuevo de Compiscript.

            function saludar(nombre: string): string {
              return "Hola " + nombre;
            }

            print(saludar("mundo"));
            """;

    private final EditorPanel editor = new EditorPanel();
    private final TreePanel treePanel = new TreePanel();
    private final SymbolTablePanel symbolsPanel = new SymbolTablePanel();
    private final TokensPanel tokensPanel = new TokensPanel();
    private final TacPanel tacPanel = new TacPanel("Sin codigo intermedio",
            "Compila con F5 un programa sin errores para generar su TAC.");
    private final TacPanel recordsPanel = new TacPanel("Sin registros de activacion",
            "Se calculan al generar el codigo intermedio.");
    private final TacPanel mipsPanel = new TacPanel("Sin codigo MIPS",
            "Compila con F5 un programa sin errores; F7 lo ejecuta en MARS.");
    private final DiagnosticsPanel diagnosticsPanel;
    private final JTextArea console = new JTextArea(8, 80);
    private final JTabbedPane bottom = new JTabbedPane();
    private final JLabel fileLabel = IdeTheme.muted("sin guardar");
    private final JLabel status = new JLabel("Compila con F5 para analizar el programa.");

    private Path currentFile;

    public IdeFrame() {
        super("Compiscript IDE");
        diagnosticsPanel = new DiagnosticsPanel(editor::goTo);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 880);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
        setJMenuBar(buildMenu());

        console.setEditable(false);
        console.setFont(IdeTheme.MONO_SMALL);
        console.setForeground(IdeTheme.INK);
        console.setBorder(IdeTheme.padding(IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP));

        JTabbedPane views = new JTabbedPane();
        views.setFont(IdeTheme.UI);
        views.addTab("Arbol sintactico", treePanel);
        views.addTab("Tabla de simbolos", symbolsPanel);
        views.addTab("Tokens", tokensPanel);
        views.addTab("Codigo intermedio", tacPanel);
        views.addTab("Registros de activacion", recordsPanel);
        views.addTab("MIPS", mipsPanel);

        bottom.setFont(IdeTheme.UI);
        bottom.addTab("Errores", diagnosticsPanel);
        bottom.addTab("Salida", IdeTheme.scroll(console));

        // La tabla de simbolos lleva nueve atributos: el panel derecho necesita
        // mas ancho que el editor, que ya respira a 620.
        JSplit top = new JSplit(JSplit.HORIZONTAL, editor, views, 620, 0.42);
        JSplit split = new JSplit(JSplit.VERTICAL, top, bottom, 640, 0.8);

        add(buildToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        editor.setSource(PLANTILLA);
    }

    /** JSplitPane sin el borde biselado de Swing y con proporciones explicitas. */
    private static final class JSplit extends javax.swing.JSplitPane {
        static final int HORIZONTAL = HORIZONTAL_SPLIT;
        static final int VERTICAL = VERTICAL_SPLIT;

        JSplit(int orientation, java.awt.Component first, java.awt.Component second,
               int divider, double weight) {
            super(orientation, first, second);
            setDividerLocation(divider);
            setResizeWeight(weight);
            setDividerSize(6);
            setBorder(BorderFactory.createEmptyBorder());
            setContinuousLayout(true);
        }
    }

    private JPanel buildToolbar() {
        JButton open = IdeTheme.button("Abrir", "Abrir un archivo .cps");
        JButton save = IdeTheme.button("Guardar", "Guardar el archivo actual");
        JButton compile = IdeTheme.primaryButton("Compilar", "Analizar el programa (F5)");
        JButton run = IdeTheme.button("Ejecutar", "Compilar y ejecutar el MIPS en MARS (F7)");
        JButton tests = IdeTheme.button("Bateria", "Correr la bateria de pruebas (F6)");

        open.addActionListener(e -> abrir());
        save.addActionListener(e -> guardar());
        compile.addActionListener(e -> compilar());
        run.addActionListener(e -> ejecutar());
        tests.addActionListener(e -> ejecutarPruebas());

        JPanel bar = IdeTheme.strip();
        bar.setBorder(BorderFactory.createCompoundBorder(
                IdeTheme.hairline(0, 0, 1, 0), IdeTheme.padding(6, IdeTheme.GAP, 6, IdeTheme.GAP)));
        bar.add(open);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(save);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(compile);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(run);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(tests);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(fileLabel);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    private JPanel buildStatusBar() {
        status.setFont(IdeTheme.UI);
        status.setForeground(IdeTheme.MUTED);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(IdeTheme.PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                IdeTheme.hairline(1, 0, 0, 0), IdeTheme.padding(5, IdeTheme.GAP, 5, IdeTheme.GAP)));
        bar.add(status, BorderLayout.WEST);
        return bar;
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Archivo");
        file.add(item("Nuevo", KeyEvent.VK_N, this::nuevo));
        file.add(item("Abrir...", KeyEvent.VK_O, this::abrir));
        file.add(item("Guardar", KeyEvent.VK_S, this::guardar));
        JMenuItem saveAs = new JMenuItem("Guardar como...");
        saveAs.addActionListener(e -> guardarComo());
        file.add(saveAs);
        file.addSeparator();
        JMenuItem exit = new JMenuItem("Salir");
        exit.addActionListener(e -> dispose());
        file.add(exit);

        JMenu build = new JMenu("Compilar");
        JMenuItem compile = new JMenuItem("Compilar");
        compile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        compile.addActionListener(e -> compilar());
        build.add(compile);
        JMenuItem run = new JMenuItem("Ejecutar en MARS");
        run.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        run.addActionListener(e -> ejecutar());
        build.add(run);
        JMenuItem tests = new JMenuItem("Ejecutar bateria de pruebas");
        tests.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        tests.addActionListener(e -> ejecutarPruebas());
        build.add(tests);

        bar.add(file);
        bar.add(build);
        return bar;
    }

    private JMenuItem item(String text, int key, Runnable action) {
        JMenuItem menuItem = new JMenuItem(text);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(key,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        menuItem.addActionListener(e -> action.run());
        return menuItem;
    }

    // ------------------------------------------------------------------ acciones

    private void nuevo() {
        currentFile = null;
        editor.setSource(PLANTILLA);
        setTitle("Compiscript IDE");
        fileLabel.setText("sin guardar");
        diagnosticsPanel.reset();
        treePanel.clear();
        symbolsPanel.setRoot(null);
        tokensPanel.setTokens(null);
        tacPanel.setText(null);
        recordsPanel.setText(null);
        mipsPanel.setText(null);
        setStatus("Archivo nuevo. Compila con F5.", IdeTheme.MUTED);
    }

    private void abrir() {
        JFileChooser chooser = new JFileChooser(Path.of(".").toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Compiscript (*.cps)", "cps"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            open(chooser.getSelectedFile().toPath());
        }
    }

    public void open(Path file) {
        try {
            editor.setSource(Files.readString(file));
            currentFile = file;
            setTitle("Compiscript IDE - " + file.getFileName());
            fileLabel.setText(file.getFileName().toString());
            compilar();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo abrir: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardar() {
        if (currentFile == null) {
            guardarComo();
            return;
        }
        try {
            Files.writeString(currentFile, editor.getSource());
            setStatus("Guardado en " + currentFile, IdeTheme.OK);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarComo() {
        JFileChooser chooser = new JFileChooser(Path.of(".").toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Compiscript (*.cps)", "cps"));
        chooser.setSelectedFile(new File(currentFile != null
                ? currentFile.getFileName().toString() : "programa.cps"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        currentFile = chooser.getSelectedFile().toPath();
        setTitle("Compiscript IDE - " + currentFile.getFileName());
        fileLabel.setText(currentFile.getFileName().toString());
        guardar();
    }

    private CompilationResult compilar() {
        String origin = currentFile != null ? currentFile.toString() : "(sin guardar)";
        CompilationResult result = CompilerService.compile(editor.getSource(), origin);

        diagnosticsPanel.setDiagnostics(result.diagnostics());
        editor.markDiagnostics(result.diagnostics());
        tokensPanel.setTokens(result.tokens());
        treePanel.show(result.tree(), result.parser());
        symbolsPanel.setRoot(result.globalScope());
        tacPanel.setText(result.tac() == null ? null : result.tac().render());
        recordsPanel.setText(result.tac() == null ? null : result.tac().recordsText());
        mipsPanel.setText(result.mips());

        int errors = result.errors().size();
        int warnings = result.warnings().size();
        if (errors > 0) {
            setStatus(errors + " error(es)" + (warnings > 0 ? ", " + warnings
                    + " advertencia(s)" : "") + ". Doble clic en la tabla salta a la linea.",
                    IdeTheme.ERROR);
            bottom.setSelectedIndex(0);
        } else if (warnings > 0) {
            setStatus("Sin errores, TAC y MIPS generados. " + warnings
                    + " advertencia(s).", IdeTheme.WARN);
        } else {
            setStatus("Sin errores ni advertencias. TAC y MIPS generados.", IdeTheme.OK);
        }

        console.append("=== " + origin + " ===\n");
        if (result.globalScope() != null) {
            console.append(result.globalScope().dump());
        }
        for (SemanticError diagnostic : result.diagnostics()) {
            console.append("  " + diagnostic + "\n");
        }
        console.append(errors + " error(es), " + warnings + " advertencia(s)\n\n");
        console.setCaretPosition(console.getDocument().getLength());
        return result;
    }

    /** MARS corre en otro proceso; se espera en segundo plano para no congelar la ventana. */
    private void ejecutar() {
        CompilationResult result = compilar();
        if (result.mips() == null) {
            return;
        }
        setStatus("Ejecutando en MARS...", IdeTheme.MUTED);
        bottom.setSelectedIndex(1);
        new SwingWorker<MarsRunner.Run, Void>() {
            @Override
            protected MarsRunner.Run doInBackground() throws IOException {
                return MarsRunner.run(result.mips());
            }

            @Override
            protected void done() {
                try {
                    MarsRunner.Run run = get();
                    console.append("--- salida de MARS ---\n" + run.output());
                    if (!run.messages().isBlank()) {
                        console.append(run.messages());
                    }
                    console.append("--- codigo de salida " + run.exitCode() + " ---\n\n");
                    setStatus(run.ok() ? "Ejecucion terminada sin errores."
                            : "La ejecucion termino con error (codigo " + run.exitCode() + ").",
                            run.ok() ? IdeTheme.OK : IdeTheme.ERROR);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    console.append("No se pudo ejecutar MARS: " + cause.getMessage() + "\n\n");
                    setStatus("No se pudo ejecutar MARS.", IdeTheme.ERROR);
                }
                console.setCaretPosition(console.getDocument().getLength());
            }
        }.execute();
    }

    /** El enunciado pide que el usuario pueda correr la bateria desde el IDE. */
    private void ejecutarPruebas() {
        JFileChooser chooser = new JFileChooser(Path.of("tests").toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Carpeta de la bateria de pruebas");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path directory = chooser.getSelectedFile().toPath();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        setStatus("Corriendo la bateria (los casos de MIPS se ejecutan en MARS)...",
                IdeTheme.MUTED);
        bottom.setSelectedIndex(1);
        // Cada caso de MIPS lanza MARS: en el hilo de la interfaz la ventana se congelaria.
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws IOException {
                try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
                    return TestRunner.run(directory, out);
                }
            }

            @Override
            protected void done() {
                try {
                    int code = get();
                    setStatus(code == 0
                            ? "Bateria: todas las pruebas pasan; reglas y construcciones cubiertas."
                            : "Bateria: hay pruebas en rojo o casos sin cubrir. Mira la salida.",
                            code == 0 ? IdeTheme.OK : IdeTheme.ERROR);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setStatus("No se pudo correr la bateria: " + cause.getMessage(),
                            IdeTheme.ERROR);
                }
                console.append(buffer.toString(StandardCharsets.UTF_8));
                console.append("\n");
                console.setCaretPosition(console.getDocument().getLength());
            }
        }.execute();
    }

    private void setStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }
}
