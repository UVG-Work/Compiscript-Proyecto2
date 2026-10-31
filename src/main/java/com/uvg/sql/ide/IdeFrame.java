package com.uvg.sql.ide;

import com.uvg.sql.pipeline.Interpreter;
import com.uvg.sql.pipeline.Report;
import com.uvg.sql.pipeline.RunResult;
import com.uvg.sql.storage.Database;
import com.uvg.sql.storage.StorageException;
import com.uvg.sql.tools.TestRunner;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * La ventana del IDE. Cubre lo que pide el enunciado: escribir SQL (editor),
 * ver tokens, arbol sintactico y AST, ver los errores de cada fase, ejecutar y
 * ver los resultados. Ademas muestra el esquema de la base abierta.
 *
 * <p>F5 analiza sin tocar la base; F6 ejecuta.
 */
public class IdeFrame extends JFrame {

    private static final String PLANTILLA = """
            -- F5 analiza el script sin ejecutarlo; F6 lo ejecuta sobre la base abierta.
            CREATE TABLE estudiantes (id INT PRIMARY KEY, nombre VARCHAR(30) NOT NULL, edad INT);
            INSERT INTO estudiantes VALUES (1, 'Ana', 20), (2, 'Bruno', 17), (3, 'Carla', 18);
            CREATE INDEX idx_edad ON estudiantes(edad);

            SELECT nombre
            FROM estudiantes
            WHERE edad >= 18;
            """;

    private final EditorPanel editor = new EditorPanel();
    private final ResultsPanel results = new ResultsPanel();
    private final TokensPanel tokens = new TokensPanel();
    private final TreePanel parseTree = new TreePanel("Sin arbol sintactico");
    private final TreePanel ast = new TreePanel("Sin AST");
    private final SchemaPanel schema = new SchemaPanel();
    private final ErrorsPanel errors;
    private final JTextArea console = new JTextArea(8, 80);
    private final JTabbedPane views = new JTabbedPane();
    private final JTabbedPane bottom = new JTabbedPane();
    private final JLabel fileLabel = IdeTheme.muted("sin guardar");
    private final JLabel dbLabel = IdeTheme.muted("");
    private final JLabel status = new JLabel("F5 analiza, F6 ejecuta.");

    private Path currentFile;
    private Database db;

    public IdeFrame(Path database) {
        super("Interprete SQL");
        errors = new ErrorsPanel(editor::goTo);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1360, 880);
        setMinimumSize(new Dimension(1000, 640));
        setLocationRelativeTo(null);
        setJMenuBar(buildMenu());

        console.setEditable(false);
        console.setFont(IdeTheme.MONO_SMALL);
        console.setForeground(IdeTheme.INK);
        console.setBorder(IdeTheme.padding(IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP, IdeTheme.GAP));

        views.setFont(IdeTheme.UI);
        views.addTab("Resultados", results);
        views.addTab("Tokens", tokens);
        views.addTab("Arbol sintactico", parseTree);
        views.addTab("AST", ast);
        views.addTab("Esquema", schema);

        bottom.setFont(IdeTheme.UI);
        bottom.addTab("Errores", errors);
        bottom.addTab("Salida", IdeTheme.scroll(console));

        JSplitPane top = split(JSplitPane.HORIZONTAL_SPLIT, editor, views, 560, 0.42);
        JSplitPane all = split(JSplitPane.VERTICAL_SPLIT, top, bottom, 640, 0.8);
        add(buildToolbar(), BorderLayout.NORTH);
        add(all, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        editor.setSource(PLANTILLA);
        openDatabase(database);
    }

    private static JSplitPane split(int orientation, java.awt.Component first,
                                    java.awt.Component second, int divider, double weight) {
        JSplitPane pane = new JSplitPane(orientation, first, second);
        pane.setDividerLocation(divider);
        pane.setResizeWeight(weight);
        pane.setDividerSize(6);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setContinuousLayout(true);
        return pane;
    }

    private JPanel buildToolbar() {
        JButton open = IdeTheme.button("Abrir", "Abrir un script .sql");
        JButton save = IdeTheme.button("Guardar", "Guardar el script");
        JButton analyze = IdeTheme.button("Analizar", "Analisis lexico, sintactico y semantico, sin ejecutar (F5)");
        JButton run = IdeTheme.primaryButton("Ejecutar", "Ejecutar el script sobre la base (F6)");
        JButton database = IdeTheme.button("Base...", "Abrir otra carpeta de base de datos");
        JButton tests = IdeTheme.button("Bateria", "Correr la bateria de pruebas (F7)");

        open.addActionListener(e -> abrir());
        save.addActionListener(e -> guardar());
        analyze.addActionListener(e -> procesar(false));
        run.addActionListener(e -> procesar(true));
        database.addActionListener(e -> elegirBase());
        tests.addActionListener(e -> bateria());

        JPanel bar = IdeTheme.strip();
        bar.setBorder(BorderFactory.createCompoundBorder(
                IdeTheme.hairline(0, 0, 1, 0), IdeTheme.padding(6, IdeTheme.GAP, 6, IdeTheme.GAP)));
        bar.add(open);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(save);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(analyze);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(run);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(fileLabel);
        bar.add(Box.createHorizontalStrut(14));
        bar.add(Box.createHorizontalGlue());
        bar.add(dbLabel);
        bar.add(Box.createHorizontalStrut(6));
        bar.add(database);
        bar.add(Box.createHorizontalStrut(4));
        bar.add(tests);
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
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenu file = new JMenu("Archivo");
        file.add(item("Abrir...", KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut), this::abrir));
        file.add(item("Guardar", KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut), this::guardar));
        file.add(item("Abrir base de datos...", null, this::elegirBase));

        JMenu sql = new JMenu("SQL");
        sql.add(item("Analizar", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), () -> procesar(false)));
        sql.add(item("Ejecutar", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), () -> procesar(true)));
        sql.add(item("Bateria de pruebas", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), this::bateria));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(sql);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke key, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(key);
        item.addActionListener(e -> action.run());
        return item;
    }

    // ------------------------------------------------------------------ acciones

    /** Corre el script y llena todas las vistas con lo que produjo cada fase. */
    private void procesar(boolean execute) {
        if (db == null) {
            setStatus("No hay una base abierta.", IdeTheme.ERROR);
            return;
        }
        RunResult run = Interpreter.run(editor.getSource(), db, execute);

        tokens.setTokens(run.parse().tokens());
        parseTree.show(run.parse().tree(), Arrays.asList(run.parse().parser().getRuleNames()));
        ast.show(run.ast(), null);
        errors.setErrors(run.errors());
        editor.markErrors(run.errors());
        results.setResults(run.executed());
        schema.show(db);

        console.append("=== " + (execute ? "Ejecucion" : "Analisis") + " ===\n");
        console.append(Report.render(run));
        console.append("\n");
        console.setCaretPosition(console.getDocument().getLength());

        if (!run.ok()) {
            setStatus(run.errors().size() + " error(es). Doble clic en un error lleva a su linea.",
                    IdeTheme.ERROR);
            bottom.setSelectedIndex(0);
        } else if (execute) {
            setStatus(run.executed().size() + " sentencia(s) ejecutada(s) sin errores.", IdeTheme.OK);
        } else {
            setStatus("Sin errores. F6 para ejecutar.", IdeTheme.OK);
        }
        if (execute && !run.executed().isEmpty()) {
            views.setSelectedComponent(results);
        }
    }

    private void openDatabase(Path directory) {
        try {
            db = Database.open(directory);
            // Solo el nombre de la carpeta: la ruta completa empujaba los botones
            // fuera de la barra. La ruta queda en el tooltip.
            Path full = directory.toAbsolutePath().normalize();
            dbLabel.setText("base: " + full.getFileName());
            dbLabel.setToolTipText(full.toString());
            schema.show(db);
            setStatus("Base abierta: " + directory, IdeTheme.MUTED);
        } catch (StorageException e) {
            db = null;
            dbLabel.setText("sin base");
            setStatus(e.getMessage(), IdeTheme.ERROR);
        }
    }

    private void elegirBase() {
        JFileChooser chooser = new JFileChooser(Path.of(".").toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Carpeta de la base de datos (se crea si esta vacia)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openDatabase(chooser.getSelectedFile().toPath());
        }
    }

    private void abrir() {
        JFileChooser chooser = new JFileChooser(Path.of(".").toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Scripts SQL (*.sql)", "sql"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            open(chooser.getSelectedFile().toPath());
        }
    }

    /** Carga un script en el editor y lo analiza (sin ejecutar). */
    public void open(Path file) {
        try {
            editor.setSource(Files.readString(file));
            currentFile = file;
            fileLabel.setText(file.getFileName().toString());
            procesar(false);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo abrir: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardar() {
        if (currentFile == null) {
            JFileChooser chooser = new JFileChooser(Path.of(".").toFile());
            chooser.setSelectedFile(new File("script.sql"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            currentFile = chooser.getSelectedFile().toPath();
            fileLabel.setText(currentFile.getFileName().toString());
        }
        try {
            Files.writeString(currentFile, editor.getSource());
            setStatus("Guardado en " + currentFile, IdeTheme.OK);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void bateria() {
        JFileChooser chooser = new JFileChooser(Path.of("tests").toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Carpeta de la bateria de pruebas");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            int code = TestRunner.run(chooser.getSelectedFile().toPath(), false, out);
            setStatus(code == 0 ? "Bateria: todas las pruebas pasan."
                    : "Bateria: hay pruebas que fallan; detalle en Salida.",
                    code == 0 ? IdeTheme.OK : IdeTheme.ERROR);
        } catch (IOException e) {
            setStatus("No se pudo correr la bateria: " + e.getMessage(), IdeTheme.ERROR);
        }
        console.append(buffer.toString(StandardCharsets.UTF_8) + "\n");
        console.setCaretPosition(console.getDocument().getLength());
        bottom.setSelectedIndex(1);
    }

    private void setStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }
}
