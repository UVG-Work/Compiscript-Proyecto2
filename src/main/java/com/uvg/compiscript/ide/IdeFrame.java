package com.uvg.compiscript.ide;

import com.uvg.compiscript.compiler.CompilationResult;
import com.uvg.compiscript.compiler.CompilerService;
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
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Ventana del IDE: editor a la izquierda, arbol / tabla de simbolos / tokens a
 * la derecha, y diagnosticos y salida abajo. Cubre el "Funcionamiento del
 * programa" del enunciado, incluida la ejecucion de la bateria de pruebas.
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
        JButton tests = IdeTheme.button("Bateria", "Correr la bateria de pruebas (F6)");

        open.addActionListener(e -> abrir());
        save.addActionListener(e -> guardar());
        compile.addActionListener(e -> compilar());
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

    private void compilar() {
        String origin = currentFile != null ? currentFile.toString() : "(sin guardar)";
        CompilationResult result = CompilerService.compile(editor.getSource(), origin);

        diagnosticsPanel.setDiagnostics(result.diagnostics());
        editor.markDiagnostics(result.diagnostics());
        tokensPanel.setTokens(result.tokens());
        treePanel.show(result.tree(), result.parser());
        symbolsPanel.setRoot(result.globalScope());

        int errors = result.errors().size();
        int warnings = result.warnings().size();
        if (errors > 0) {
            setStatus(errors + " error(es)" + (warnings > 0 ? ", " + warnings
                    + " advertencia(s)" : "") + ". Doble clic en la tabla salta a la linea.",
                    IdeTheme.ERROR);
            bottom.setSelectedIndex(0);
        } else if (warnings > 0) {
            setStatus("Sin errores. " + warnings + " advertencia(s).", IdeTheme.WARN);
        } else {
            setStatus("Sin errores ni advertencias.", IdeTheme.OK);
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
    }

    /** El enunciado pide que el usuario pueda correr la bateria desde el IDE. */
    private void ejecutarPruebas() {
        JFileChooser chooser = new JFileChooser(Path.of("tests").toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Carpeta de la bateria de pruebas");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            int code = TestRunner.run(chooser.getSelectedFile().toPath(), out);
            setStatus(code == 0
                    ? "Bateria: todas las pruebas pasan y las 27 reglas estan cubiertas."
                    : "Bateria: hay pruebas en rojo o reglas sin cubrir. Mira la salida.",
                    code == 0 ? IdeTheme.OK : IdeTheme.ERROR);
        } catch (IOException e) {
            setStatus("No se pudo correr la bateria: " + e.getMessage(), IdeTheme.ERROR);
        }
        console.append(buffer.toString(StandardCharsets.UTF_8));
        console.append("\n");
        console.setCaretPosition(console.getDocument().getLength());
        bottom.setSelectedIndex(1);
    }

    private void setStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }
}
