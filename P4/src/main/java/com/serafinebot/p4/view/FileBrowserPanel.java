package com.serafinebot.p4.view;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Embedded file browser with directory tree, file list, sorting, compact path display, and automatic refresh.
 */
public final class FileBrowserPanel extends JPanel {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_VISIBLE_SEGMENTS = 4;

    private final DirectoryNode rootNode = new DirectoryNode(null);
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree directoryTree = new JTree(treeModel);
    private final FileTableModel fileTableModel = new FileTableModel();
    private final JTable fileTable = new JTable(fileTableModel);
    private final TableRowSorter<FileTableModel> fileSorter = new TableRowSorter<>(fileTableModel);
    private final JLabel currentDirectoryLabel = new JLabel("Carpeta actual: -");
    private final JLabel pathTrailLabel = new JLabel();
    private final JButton backButton = new JButton("Enrere");
    private final JButton upButton = new JButton("Amunt");
    private final JToggleButton showHiddenToggle = new JToggleButton("Mostra ocults");

    private Consumer<Path> fileActivationListener = path -> {
    };
    private final List<Path> history = new ArrayList<>();
    private int historyIndex = -1;
    private boolean suppressHistoryRegistration;
    private Path currentDirectory;
    private Path watchedDirectory;
    private DirectoryNode currentNode;
    private WatchService directoryWatchService;
    private Thread directoryWatcherThread;

    public FileBrowserPanel() {
        super(new BorderLayout(8, 8));
        setOpaque(false);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(189, 198, 210)), "Explorador de fitxers"));

        buildRootNodes();
        configureTree();
        configureTable();

        JScrollPane treeScroll = new JScrollPane(directoryTree);
        JScrollPane tableScroll = new JScrollPane(fileTable);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, tableScroll);
        splitPane.setResizeWeight(0.32);
        splitPane.setBorder(null);
        splitPane.setContinuousLayout(true);

        add(createNavigationPanel(), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        Path homeDirectory = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (Files.isDirectory(homeDirectory)) {
            selectDirectory(homeDirectory, true);
        }
    }

    public void setFileActivationListener(Consumer<Path> fileActivationListener) {
        this.fileActivationListener = fileActivationListener;
    }

    public Path selectedPath() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return fileTableModel.entryAt(fileTable.convertRowIndexToModel(selectedRow)).path();
    }

    public void refreshCurrentDirectory() {
        SwingUtilities.invokeLater(() -> {
            if (currentDirectory != null) {
                fileTableModel.setDirectory(currentDirectory);
                updateCompactPathLabel(currentDirectory);
            }
            if (currentNode != null) {
                currentNode.loaded = false;
                loadChildren(currentNode);
                treeModel.nodeStructureChanged(currentNode);
            }
        });
    }

    public void setBrowserEnabled(boolean enabled) {
        directoryTree.setEnabled(enabled);
        fileTable.setEnabled(enabled);
        backButton.setEnabled(enabled && historyIndex > 0);
        upButton.setEnabled(enabled && currentDirectory != null && currentDirectory.getParent() != null);
        showHiddenToggle.setEnabled(enabled);
    }

    public void disposeBrowser() {
        stopWatchingDirectory();
    }

    private JPanel createNavigationPanel() {
        JPanel container = new JPanel(new BorderLayout(8, 6));
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(0, 2, 0, 2));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonRow.setOpaque(false);
        backButton.addActionListener(event -> navigateBack());
        upButton.addActionListener(event -> navigateUp());
        showHiddenToggle.setFocusable(false);
        showHiddenToggle.addActionListener(event -> refreshTreeAndDirectory());
        buttonRow.add(backButton);
        buttonRow.add(upButton);
        buttonRow.add(showHiddenToggle);

        JPanel pathContainer = new JPanel();
        pathContainer.setLayout(new BoxLayout(pathContainer, BoxLayout.Y_AXIS));
        pathContainer.setOpaque(false);
        currentDirectoryLabel.setFont(currentDirectoryLabel.getFont().deriveFont(Font.PLAIN, 12f));
        currentDirectoryLabel.setForeground(new Color(86, 96, 112));
        pathTrailLabel.setFont(pathTrailLabel.getFont().deriveFont(Font.BOLD, 12f));
        pathTrailLabel.setForeground(new Color(39, 73, 132));
        pathContainer.add(currentDirectoryLabel);
        pathContainer.add(pathTrailLabel);

        container.add(buttonRow, BorderLayout.WEST);
        container.add(pathContainer, BorderLayout.CENTER);
        return container;
    }

    private void buildRootNodes() {
        for (Path rootPath : FileSystems.getDefault().getRootDirectories()) {
            rootNode.add(new DirectoryNode(rootPath));
        }
    }

    private void configureTree() {
        directoryTree.setRootVisible(false);
        directoryTree.setShowsRootHandles(true);
        directoryTree.setCellRenderer(new DirectoryTreeRenderer());
        directoryTree.addTreeSelectionListener(this::onTreeSelectionChanged);
        directoryTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                Object component = event.getPath().getLastPathComponent();
                if (component instanceof DirectoryNode node) {
                    loadChildren(node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // No-op.
            }
        });
    }

    private void configureTable() {
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setFillsViewportHeight(true);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.setRowHeight(22);
        fileTable.setAutoCreateRowSorter(false);
        fileSorter.setComparator(0, Comparator.comparing(FileEntry::sortName, String.CASE_INSENSITIVE_ORDER));
        fileSorter.setComparator(1, Comparator.comparing(FileEntry::type, String.CASE_INSENSITIVE_ORDER));
        fileSorter.setComparator(2, Comparator.comparingLong(FileEntry::sizeBytes));
        fileSorter.setComparator(3, Comparator.comparingLong(FileEntry::modifiedMillis));
        fileTable.setRowSorter(fileSorter);
        fileSorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));

        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileNameRenderer());
        fileTable.getColumnModel().getColumn(2).setCellRenderer(new RightAlignedRenderer());
        fileTable.getColumnModel().getColumn(3).setCellRenderer(new ModifiedRenderer());
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(260);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(130);

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) {
                    return;
                }
                Path path = selectedPath();
                if (path == null) {
                    return;
                }
                if (Files.isDirectory(path)) {
                    selectDirectory(path, true);
                } else {
                    fileActivationListener.accept(path);
                }
            }
        });
    }

    private void onTreeSelectionChanged(TreeSelectionEvent event) {
        Object component = event.getPath() == null ? null : event.getPath().getLastPathComponent();
        if (!(component instanceof DirectoryNode node) || node.path() == null) {
            return;
        }

        currentNode = node;
        currentDirectory = node.path();
        currentDirectoryLabel.setText("Carpeta actual: " + currentDirectory);
        fileTableModel.setDirectory(currentDirectory);
        updateCompactPathLabel(currentDirectory);
        startWatchingDirectory(currentDirectory);
        if (!suppressHistoryRegistration) {
            pushHistory(currentDirectory);
        }
        updateNavigationButtons();
    }

    private void loadChildren(DirectoryNode node) {
        if (node.loaded || node.path() == null) {
            return;
        }

        node.removeAllChildren();
        List<Path> directories;
        try (Stream<Path> stream = Files.list(node.path())) {
            directories = stream
                .filter(this::isVisiblePath)
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName() == null ? path.toString() : path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        } catch (IOException exception) {
            directories = List.of();
        }

        for (Path directory : directories) {
            node.add(new DirectoryNode(directory));
        }
        node.loaded = true;
    }

    private void selectDirectory(Path directory, boolean addToHistory) {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        DirectoryNode root = findMatchingRoot(normalizedDirectory);
        if (root == null) {
            return;
        }

        loadChildren(root);
        DirectoryNode current = root;
        List<Path> segments = pathChain(root.path(), normalizedDirectory);
        for (Path segment : segments) {
            loadChildren(current);
            current = findChild(current, segment);
            if (current == null) {
                return;
            }
        }

        TreePath treePath = new TreePath(current.getPath());
        suppressHistoryRegistration = !addToHistory;
        directoryTree.setSelectionPath(treePath);
        directoryTree.scrollPathToVisible(treePath);
        suppressHistoryRegistration = false;
        updateNavigationButtons();
    }

    private DirectoryNode findMatchingRoot(Path directory) {
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DirectoryNode candidate = (DirectoryNode) rootNode.getChildAt(i);
            Path candidatePath = candidate.path();
            if (candidatePath != null && directory.startsWith(candidatePath)) {
                return candidate;
            }
        }
        return null;
    }

    private List<Path> pathChain(Path rootPath, Path targetPath) {
        List<Path> chain = new ArrayList<>();
        Path current = targetPath;
        while (current != null && !current.equals(rootPath)) {
            chain.add(0, current);
            current = current.getParent();
        }
        return chain;
    }

    private DirectoryNode findChild(DirectoryNode parent, Path childPath) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DirectoryNode child = (DirectoryNode) parent.getChildAt(i);
            if (childPath.equals(child.path())) {
                return child;
            }
        }
        return null;
    }

    private synchronized void startWatchingDirectory(Path directory) {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        if (normalizedDirectory.equals(watchedDirectory) && directoryWatchService != null) {
            return;
        }

        stopWatchingDirectory();

        try {
            directoryWatchService = FileSystems.getDefault().newWatchService();
            normalizedDirectory.register(
                directoryWatchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            );
            watchedDirectory = normalizedDirectory;
            directoryWatcherThread = new Thread(this::watchLoop, "p4-file-browser-watcher");
            directoryWatcherThread.setDaemon(true);
            directoryWatcherThread.start();
        } catch (IOException exception) {
            watchedDirectory = null;
            directoryWatchService = null;
            directoryWatcherThread = null;
        }
    }

    private synchronized void stopWatchingDirectory() {
        watchedDirectory = null;
        if (directoryWatcherThread != null) {
            directoryWatcherThread.interrupt();
            directoryWatcherThread = null;
        }
        if (directoryWatchService != null) {
            try {
                directoryWatchService.close();
            } catch (IOException ignored) {
                // Best effort cleanup.
            }
            directoryWatchService = null;
        }
    }

    private void watchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = directoryWatchService.take();
                boolean changed = false;
                if (key != null) {
                    for (Object ignored : key.pollEvents()) {
                        changed = true;
                    }
                    key.reset();
                }
                if (changed) {
                    refreshCurrentDirectory();
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ClosedWatchServiceException ignored) {
            // Normal shutdown path.
        }
    }

    private void pushHistory(Path directory) {
        if (historyIndex >= 0 && historyIndex < history.size() && history.get(historyIndex).equals(directory)) {
            return;
        }
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(directory);
        historyIndex = history.size() - 1;
    }

    private void navigateBack() {
        if (historyIndex <= 0) {
            return;
        }
        historyIndex--;
        selectDirectory(history.get(historyIndex), false);
        updateNavigationButtons();
    }

    private void navigateUp() {
        if (currentDirectory == null || currentDirectory.getParent() == null) {
            return;
        }
        selectDirectory(currentDirectory.getParent(), true);
    }

    private void updateCompactPathLabel(Path directory) {
        List<Path> breadcrumbPaths = buildBreadcrumbPaths(directory);
        StringBuilder builder = new StringBuilder();
        if (breadcrumbPaths.size() > MAX_VISIBLE_SEGMENTS) {
            builder.append("... > ");
            breadcrumbPaths = breadcrumbPaths.subList(breadcrumbPaths.size() - MAX_VISIBLE_SEGMENTS, breadcrumbPaths.size());
        }

        for (int i = 0; i < breadcrumbPaths.size(); i++) {
            if (i > 0) {
                builder.append(" > ");
            }
            builder.append(breadcrumbLabel(breadcrumbPaths.get(i)));
        }
        pathTrailLabel.setText(builder.toString());
    }

    private List<Path> buildBreadcrumbPaths(Path directory) {
        List<Path> paths = new ArrayList<>();
        Path current = directory;
        while (current != null) {
            paths.add(0, current);
            current = current.getParent();
        }
        return paths;
    }

    private String breadcrumbLabel(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(historyIndex > 0);
        upButton.setEnabled(currentDirectory != null && currentDirectory.getParent() != null);
    }

    private void refreshTreeAndDirectory() {
        markUnloaded(rootNode);
        treeModel.reload();
        if (currentDirectory != null) {
            selectDirectory(currentDirectory, false);
            refreshCurrentDirectory();
        }
    }

    private void markUnloaded(DirectoryNode node) {
        node.loaded = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            markUnloaded((DirectoryNode) node.getChildAt(i));
        }
    }

    private boolean isVisiblePath(Path path) {
        if (showHiddenToggle.isSelected()) {
            return true;
        }
        Path fileName = path.getFileName();
        return fileName == null || !fileName.toString().startsWith(".");
    }

    private static final class DirectoryNode extends DefaultMutableTreeNode {
        private boolean loaded;

        private DirectoryNode(Path path) {
            super(path);
        }

        private Path path() {
            return (Path) getUserObject();
        }
    }

    private static final class DirectoryTreeRenderer extends DefaultTreeCellRenderer {
        private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DirectoryNode node && node.path() != null) {
                Path path = node.path();
                java.io.File file = path.toFile();
                Path fileName = path.getFileName();
                setText(fileName == null ? path.toString() : fileName.toString());
                setIcon(fileSystemView.getSystemIcon(file));
                setIconTextGap(6);
            }
            return this;
        }
    }

    private final class FileTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Nom", "Tipus", "Mida", "Modificat"};
        private final List<FileEntry> entries = new ArrayList<>();

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> FileEntry.class;
                case 1 -> String.class;
                case 2 -> Long.class;
                case 3 -> Long.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry;
                case 1 -> entry.type();
                case 2 -> entry.sizeBytes();
                case 3 -> entry.modifiedMillis();
                default -> null;
            };
        }

        private void setDirectory(Path directory) {
            entries.clear();
            try (Stream<Path> stream = Files.list(directory)) {
                List<Path> paths = stream
                    .filter(FileBrowserPanel.this::isVisiblePath)
                    .sorted(Comparator
                        .comparing((Path path) -> !Files.isDirectory(path))
                        .thenComparing(path -> path.getFileName() == null ? path.toString() : path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
                for (Path path : paths) {
                    entries.add(FileEntry.from(path));
                }
            } catch (IOException ignored) {
                // Leave the table empty if the directory cannot be listed.
            }
            fireTableDataChanged();
        }

        private FileEntry entryAt(int rowIndex) {
            return entries.get(rowIndex);
        }
    }

    private static final class FileNameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof FileEntry entry) {
                setText(entry.name());
                setIcon(entry.icon());
                setIconTextGap(8);
            }
            return this;
        }
    }

    private static final class RightAlignedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(RIGHT);
            if (value instanceof Long longValue) {
                int modelRow = table.convertRowIndexToModel(row);
                FileEntry entry = ((FileTableModel) table.getModel()).entryAt(modelRow);
                setText(entry.sizeLabel());
            }
            return this;
        }
    }

    private static final class ModifiedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof Long) {
                int modelRow = table.convertRowIndexToModel(row);
                FileEntry entry = ((FileTableModel) table.getModel()).entryAt(modelRow);
                setText(entry.modifiedLabel());
            }
            return this;
        }
    }

    private record FileEntry(Path path,
                             String name,
                             String type,
                             long sizeBytes,
                             String sizeLabel,
                             long modifiedMillis,
                             String modifiedLabel,
                             String sortName,
                             Icon icon) {
        private static FileEntry from(Path path) throws IOException {
            boolean directory = Files.isDirectory(path);
            java.io.File file = path.toFile();
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String type = directory ? "Carpeta" : extension(path);
            long sizeBytes = directory ? -1L : Files.size(path);
            String sizeLabel = directory ? "" : formatSize(sizeBytes);
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            long modifiedMillis = lastModifiedTime.toMillis();
            String modifiedLabel = DATE_FORMATTER.format(Instant.ofEpochMilli(modifiedMillis).atZone(ZoneId.systemDefault()));
            return new FileEntry(
                path,
                name,
                type,
                sizeBytes,
                sizeLabel,
                modifiedMillis,
                modifiedLabel,
                name.toLowerCase(),
                FileSystemView.getFileSystemView().getSystemIcon(file)
            );
        }

        private static String extension(Path path) {
            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex < 0 || dotIndex == name.length() - 1) {
                return "Fitxer";
            }
            return name.substring(dotIndex + 1).toUpperCase();
        }

        private static String formatSize(long size) {
            String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
            double value = size;
            int unitIndex = 0;
            while (value >= 1024.0 && unitIndex < units.length - 1) {
                value /= 1024.0;
                unitIndex++;
            }
            if (unitIndex == 0) {
                return size + " " + units[unitIndex];
            }
            return String.format("%.2f %s", value, units[unitIndex]);
        }
    }
}
