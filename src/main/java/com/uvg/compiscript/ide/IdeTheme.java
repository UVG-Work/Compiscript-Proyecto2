package com.uvg.compiscript.ide;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * El vocabulario visual del IDE, en un solo lugar.
 *
 * <p>Es una superficie de trabajo, no una pieza expresiva: paleta contenida,
 * una sola familia tipografica para el cromo y una monoespaciada para todo lo
 * que es codigo o dato. El acento se reserva para la accion principal, la
 * seleccion y los indicadores de estado; nunca decora.
 */
public final class IdeTheme {

    public static final Color INK = new Color(0x1F2328);
    public static final Color MUTED = new Color(0x656D76);
    public static final Color LINE = new Color(0xD8DDE3);
    public static final Color PANEL = new Color(0xF3F5F7);
    public static final Color SURFACE = Color.WHITE;
    public static final Color ZEBRA = new Color(0xFAFBFC);

    public static final Color ACCENT = new Color(0x1F5FA8);
    public static final Color ACCENT_SOFT = new Color(0xDCE8F6);

    public static final Color ERROR = new Color(0xB42318);
    public static final Color ERROR_TINT = new Color(0xFDE7E5);
    public static final Color WARN = new Color(0x8A6100);
    public static final Color WARN_TINT = new Color(0xFCF3DC);
    public static final Color OK = new Color(0x1A7F4B);

    public static final Font UI = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font UI_BOLD = UI.deriveFont(Font.BOLD);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    public static final Font MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public static final int GAP = 8;

    private IdeTheme() {
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** Una linea de 1px como separador: el borde biselado de Swing es ruido. */
    public static Border hairline(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, LINE);
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UI);
        label.setForeground(MUTED);
        return label;
    }

    /** Barra de cromo: fondo de panel y una sola linea que la separa del contenido. */
    public static JPanel strip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));
        strip.setBackground(PANEL);
        strip.setBorder(BorderFactory.createCompoundBorder(
                hairline(0, 0, 1, 0), padding(5, GAP, 5, GAP)));
        return strip;
    }

    public static JButton button(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFont(UI);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        return button;
    }

    /**
     * La accion principal se pinta sola: con el look and feel del sistema,
     * {@code setBackground} en un JButton se ignora.
     */
    public static JButton primaryButton(String text, String tooltip) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = getModel().isPressed() ? ACCENT.darker()
                        : getModel().isRollover() ? ACCENT.brighter() : ACCENT;
                g2.setColor(isEnabled() ? fill : LINE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(UI_BOLD);
        button.setForeground(Color.WHITE);
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(padding(6, 14, 6, 14));
        return button;
    }

    /** Densidad legible: filas altas, cebra suave y encabezado que no grita. */
    public static void styleTable(JTable table) {
        table.setFont(UI);
        table.setRowHeight(22);
        table.setShowVerticalLines(false);
        table.setGridColor(LINE);
        table.setSelectionBackground(ACCENT_SOFT);
        table.setSelectionForeground(INK);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JTableHeader header = table.getTableHeader();
        header.setFont(UI_BOLD);
        header.setForeground(MUTED);
        header.setBackground(PANEL);
        header.setBorder(hairline(0, 0, 1, 0));
        header.setReorderingAllowed(false);
    }

    public static void widths(JTable table, int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Renderer base: cebra, sangria y tipografia por columna. */
    public static class CellRenderer extends DefaultTableCellRenderer {

        private final boolean monospace;

        public CellRenderer(boolean monospace) {
            this.monospace = monospace;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setFont(monospace ? MONO_SMALL : UI);
            setBorder(padding(0, 8, 0, 8));
            if (!selected) {
                setBackground(row % 2 == 0 ? SURFACE : ZEBRA);
                setForeground(foregroundFor(table, row, column));
            }
            return this;
        }

        /** Las subclases pintan el texto segun la fila. */
        protected Color foregroundFor(JTable table, int row, int column) {
            return INK;
        }
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * Estado vacio que ensena la interfaz en vez de mostrar una rejilla en
     * blanco: dice que pasa y cual es el siguiente paso.
     */
    public static JPanel emptyState(String headline, String hint) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(SURFACE);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(headline);
        title.setFont(UI.deriveFont(Font.BOLD, 13f));
        title.setForeground(INK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel(hint);
        subtitle.setFont(UI);
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        stack.add(title);
        stack.add(Box.createVerticalStrut(4));
        stack.add(subtitle);
        panel.add(stack);
        return panel;
    }

    /** Cuadrito de color que identifica el tipo de ambito en el arbol. */
    public static final class Dot implements javax.swing.Icon {

        private final Color fill;
        private final boolean hollow;

        public Dot(Color fill, boolean hollow) {
            this.fill = fill;
            this.hollow = hollow;
        }

        @Override
        public void paintIcon(Component host, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            if (hollow) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(x + 1, y + 1, 8, 8, 3, 3);
            } else {
                g2.fillRoundRect(x + 1, y + 1, 9, 9, 3, 3);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    /** Panel con una barra de titulo discreta encima del contenido. */
    public static JPanel section(JComponent bar, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        if (bar != null) {
            panel.add(bar, BorderLayout.NORTH);
        }
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    public static JLabel headerLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(UI_BOLD);
        label.setForeground(MUTED);
        return label;
    }
}
