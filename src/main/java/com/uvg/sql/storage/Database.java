package com.uvg.sql.storage;

import com.uvg.sql.catalog.Catalog;
import com.uvg.sql.catalog.Column;
import com.uvg.sql.catalog.IndexDef;
import com.uvg.sql.catalog.TableSchema;
import com.uvg.sql.types.DataType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Una base de datos guardada en un directorio:
 *
 * <pre>
 * database/
 * |-- metadata.txt      esquema de las tablas y lista de indices
 * |-- estudiantes.csv   registros de la tabla, uno por linea
 * |-- idx_edad.idx      indice: valor -> rowids
 * </pre>
 *
 * <p>Los archivos son la fuente de verdad: al abrir se lee metadata.txt y
 * cada tabla o indice se carga la primera vez que se usa. Despues de cada
 * sentencia que modifica datos el interprete llama a {@link #save(Table)}, que
 * reescribe el CSV y los indices de la tabla. Cada escritura va a un archivo
 * temporal que luego reemplaza al original, para que un corte a medias no deje
 * un archivo truncado.
 */
public final class Database {

    static final String METADATA = "metadata.txt";
    private static final String ROWID = "_rowid";
    private static final Pattern INDEX_LINE =
            Pattern.compile("INDEX (\\w+) ON (\\w+)\\((\\w+)\\)");

    private final Path directory;
    private final Catalog catalog;
    private final Map<String, Table> tables = new HashMap<>();
    private final Map<String, Index> indexes = new HashMap<>();

    private Database(Path directory, Catalog catalog) {
        this.directory = directory;
        this.catalog = catalog;
    }

    /** Abre la base del directorio; si no existe, la crea vacia. */
    public static Database open(Path directory) {
        try {
            Files.createDirectories(directory);
            Path metadata = directory.resolve(METADATA);
            Catalog catalog = Files.exists(metadata) ? readMetadata(metadata) : new Catalog();
            return new Database(directory, catalog);
        } catch (IOException e) {
            throw new StorageException("no se pudo abrir la base en " + directory + ": " + e.getMessage(), e);
        }
    }

    /** El catalogo real. Solo lo modifican los metodos de definicion de esta clase. */
    public Catalog catalog() {
        return catalog;
    }

    // ------------------------------------------------------------ definicion

    public void createTable(TableSchema schema) {
        catalog.addTable(schema);
        Table table = new Table(schema);
        tables.put(schema.name(), table);
        writeTable(table);
        writeMetadata();
    }

    /** Borra la tabla, su CSV y sus indices. */
    public void dropTable(String name) {
        for (IndexDef index : catalog.indexesOf(name)) {
            indexes.remove(index.name());
            delete(indexFile(index.name()));
        }
        catalog.removeTable(name);
        tables.remove(name);
        delete(tableFile(name));
        writeMetadata();
    }

    /** Crea el indice recorriendo la tabla una vez y lo guarda en su archivo. */
    public void createIndex(IndexDef definition) {
        Table table = table(definition.table());
        Index index = new Index(definition, table.schema().indexOf(definition.column()));
        for (long rowId : table.rowIds()) {
            index.add(table.row(rowId)[index.column()], rowId);
        }
        catalog.addIndex(definition);
        indexes.put(definition.name(), index);
        writeIndex(index);
        writeMetadata();
    }

    public void dropIndex(String name) {
        catalog.removeIndex(name);
        indexes.remove(name);
        delete(indexFile(name));
        writeMetadata();
    }

    // ------------------------------------------------------------------ datos

    public Table table(String name) {
        Table table = tables.get(name);
        if (table == null) {
            table = readTable(catalog.table(name));
            tables.put(name, table);
        }
        return table;
    }

    /** @return el indice sobre esa columna, o null si no tiene. */
    public Index indexOn(String table, String column) {
        IndexDef definition = catalog.indexOn(table, column);
        return definition == null ? null : index(definition);
    }

    /** Agrega el registro y lo registra en los indices de la tabla. */
    public long insert(Table table, Object[] row) {
        long rowId = table.insert(row);
        for (Index index : indexesOf(table)) {
            index.add(row[index.column()], rowId);
        }
        return rowId;
    }

    /** Reemplaza el registro; solo los indices cuya columna cambio se tocan. */
    public void update(Table table, long rowId, Object[] newRow) {
        Object[] oldRow = table.row(rowId);
        for (Index index : indexesOf(table)) {
            Object before = oldRow[index.column()];
            Object after = newRow[index.column()];
            if (!Objects.equals(before, after)) {
                index.remove(before, rowId);
                index.add(after, rowId);
            }
        }
        table.replace(rowId, newRow);
    }

    public void delete(Table table, long rowId) {
        Object[] row = table.row(rowId);
        for (Index index : indexesOf(table)) {
            index.remove(row[index.column()], rowId);
        }
        table.delete(rowId);
    }

    /** Escribe a disco la tabla y sus indices. */
    public void save(Table table) {
        writeTable(table);
        indexesOf(table).forEach(this::writeIndex);
    }

    private List<Index> indexesOf(Table table) {
        List<Index> result = new ArrayList<>();
        for (IndexDef definition : catalog.indexesOf(table.schema().name())) {
            result.add(index(definition));
        }
        return result;
    }

    private Index index(IndexDef definition) {
        Index index = indexes.get(definition.name());
        if (index == null) {
            index = readIndex(definition);
            indexes.put(definition.name(), index);
        }
        return index;
    }

    // -------------------------------------------------------------- metadata

    private void writeMetadata() {
        StringBuilder out = new StringBuilder();
        out.append("# Esquema de la base de datos. Lo escribe el interprete en cada CREATE o DROP.\n");
        for (TableSchema table : catalog.tables()) {
            out.append("\nTABLE ").append(table.name()).append('\n');
            for (Column column : table.columns()) {
                out.append("  COLUMN ").append(column.name()).append(' ').append(column.type());
                if (column.primaryKey()) {
                    out.append(" PRIMARY KEY");
                }
                if (column.notNull()) {
                    out.append(" NOT NULL");
                }
                out.append('\n');
            }
            out.append("END\n");
        }
        if (!catalog.indexes().isEmpty()) {
            out.append('\n');
        }
        for (IndexDef index : catalog.indexes()) {
            out.append("INDEX ").append(index.name()).append(" ON ").append(index.table())
                    .append('(').append(index.column()).append(")\n");
        }
        write(directory.resolve(METADATA), out.toString());
    }

    private static Catalog readMetadata(Path file) throws IOException {
        Catalog catalog = new Catalog();
        String table = null;
        List<Column> columns = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                if (line.startsWith("TABLE ")) {
                    table = line.substring(6).trim();
                    columns = new ArrayList<>();
                } else if (line.startsWith("COLUMN ") && table != null) {
                    columns.add(parseColumn(line.substring(7).trim()));
                } else if (line.equals("END") && table != null) {
                    catalog.addTable(new TableSchema(table, columns));
                    table = null;
                } else if (line.startsWith("INDEX ")) {
                    Matcher m = INDEX_LINE.matcher(line);
                    if (!m.matches()) {
                        throw new IllegalArgumentException("indice mal escrito");
                    }
                    catalog.addIndex(new IndexDef(m.group(1), m.group(2), m.group(3)));
                } else {
                    throw new IllegalArgumentException("linea no reconocida");
                }
            } catch (IllegalArgumentException e) {
                throw new StorageException(file + " linea " + (n + 1) + ": " + e.getMessage(), e);
            }
        }
        if (table != null) {
            throw new StorageException(file + ": falta END de la tabla " + table);
        }
        return catalog;
    }

    /** {@code nombre VARCHAR(20) PRIMARY KEY NOT NULL} */
    private static Column parseColumn(String text) {
        String[] parts = text.split("\\s+", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("columna mal escrita");
        }
        String rest = parts.length == 3 ? parts[2] : "";
        return new Column(parts[0], DataType.parse(parts[1]),
                rest.contains("PRIMARY KEY"), rest.contains("NOT NULL"));
    }

    // ----------------------------------------------------------------- tablas

    private Path tableFile(String table) {
        return directory.resolve(table + ".csv");
    }

    private void writeTable(Table table) {
        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>();
        header.add(ROWID);
        table.schema().columns().forEach(column -> header.add(column.name()));
        out.append(Csv.join(header)).append('\n');
        for (long rowId : table.rowIds()) {
            List<String> fields = new ArrayList<>();
            fields.add(Long.toString(rowId));
            for (Object value : table.row(rowId)) {
                fields.add(Csv.encode(value));
            }
            out.append(Csv.join(fields)).append('\n');
        }
        write(tableFile(table.schema().name()), out.toString());
    }

    private Table readTable(TableSchema schema) {
        Path file = tableFile(schema.name());
        Table table = new Table(schema);
        List<String> lines = readLines(file);
        int width = schema.columns().size();
        // La linea 1 es el encabezado; los registros empiezan en la 2.
        for (int n = 1; n < lines.size(); n++) {
            if (lines.get(n).isEmpty()) {
                continue;
            }
            try {
                List<Csv.Field> fields = Csv.split(lines.get(n));
                if (fields.size() != width + 1) {
                    throw new IllegalArgumentException("tiene " + (fields.size() - 1)
                            + " columnas y la tabla " + width);
                }
                Object[] row = new Object[width];
                for (int c = 0; c < width; c++) {
                    row[c] = Csv.decode(fields.get(c + 1), schema.column(c).type());
                }
                table.load(Long.parseLong(fields.get(0).text()), row);
            } catch (IllegalArgumentException e) {
                throw new StorageException(file + " linea " + (n + 1) + ": " + e.getMessage(), e);
            }
        }
        return table;
    }

    // ---------------------------------------------------------------- indices

    private Path indexFile(String index) {
        return directory.resolve(index + ".idx");
    }

    /** Una linea por valor: {@code valor<TAB>rowid,rowid,...}, en orden de valor. */
    private void writeIndex(Index index) {
        IndexDef def = index.definition();
        StringBuilder out = new StringBuilder();
        out.append("# INDEX ").append(def.name()).append(" ON ").append(def.table())
                .append('(').append(def.column()).append(")\n");
        for (Map.Entry<Object, Set<Long>> entry : index.entries().entrySet()) {
            out.append(Csv.encode(entry.getKey())).append('\t')
                    .append(entry.getValue().stream().map(String::valueOf)
                            .collect(Collectors.joining(",")))
                    .append('\n');
        }
        write(indexFile(def.name()), out.toString());
    }

    private Index readIndex(IndexDef definition) {
        TableSchema schema = catalog.table(definition.table());
        int column = schema.indexOf(definition.column());
        DataType type = schema.column(column).type();
        Index index = new Index(definition, column);
        Path file = indexFile(definition.name());
        List<String> lines = readLines(file);
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n);
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                int tab = line.lastIndexOf('\t');
                if (tab < 0) {
                    throw new IllegalArgumentException("falta el tabulador entre valor y rowids");
                }
                Object value = Csv.decode(Csv.split(line.substring(0, tab)).get(0), type);
                for (String id : line.substring(tab + 1).split(",")) {
                    index.add(value, Long.parseLong(id));
                }
            } catch (IllegalArgumentException e) {
                throw new StorageException(file + " linea " + (n + 1) + ": " + e.getMessage(), e);
            }
        }
        return index;
    }

    // ---------------------------------------------------------------- archivos

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("no se pudo leer " + file + ": " + e.getMessage(), e);
        }
    }

    /** Escribe en un temporal y lo mueve encima del original. */
    private static void write(Path file, String content) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StorageException("no se pudo escribir " + file + ": " + e.getMessage(), e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new StorageException("no se pudo borrar " + file + ": " + e.getMessage(), e);
        }
    }
}
