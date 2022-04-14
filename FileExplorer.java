package FileExplorer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileExplorer extends JPanel {

    public final static int WIDTH = 500;
    public final static int HEIGHT = 500;

    FileSystemView fileSystemView;
    Desktop desktop;

    JPanel navigation;
    JTree tree;
    JTable table;
    JScrollPane treeScroll, tableScroll;
    JButton bOpenFile, bNewFile, bPrev, bNext, bDeleteFile, bCopy, bPaste;

    DefaultTreeModel treeModel;
    FileTableModel tableModel;

    File selectedDirectory;
    File selectedFileInTable;
    File currentDirectory;
    File copiedFile;

    ListSelectionListener tableModelListener;
    ButtonAction buttonAction;

    List<String> history = new ArrayList<>();

    public FileExplorer() {
        setLayout(new BorderLayout(3, 3));
        setBorder(new EmptyBorder(5, 5, 5, 5));

        fileSystemView = FileSystemView.getFileSystemView();
        desktop = Desktop.getDesktop();

//  Navigation
        navigation = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonAction = new ButtonAction();

        bPrev = new JButton();
        try {
            Image imgPrev = ImageIO.read(getClass().getResource("arrowLeft.png"));
            bPrev.setIcon(new ImageIcon(imgPrev.getScaledInstance(20, 20, 1)));
            bPrev.setOpaque(true);
            bPrev.setActionCommand("Prev");
            bPrev.addActionListener(buttonAction);
        } catch (Exception bPrevEx) {
            bPrevEx.printStackTrace();
        }

        bNext = new JButton();
        try {
            Image imgPrev = ImageIO.read(getClass().getResource("arrowRight.png"));
            bNext.setIcon(new ImageIcon(imgPrev.getScaledInstance(20, 20, 1)));
            bNext.setOpaque(true);
            bNext.setActionCommand("Next");
            bNext.addActionListener(buttonAction);
        } catch (Exception bPrevEx) {
            bPrevEx.printStackTrace();
        }

        bNewFile = new JButton("New");
        bNewFile.addActionListener(buttonAction);

        bOpenFile = new JButton("Open");
        bOpenFile.addActionListener(buttonAction);

        bDeleteFile = new JButton("Delete");
        bDeleteFile.addActionListener(buttonAction);

        bCopy = new JButton("Copy");
        bCopy.addActionListener(buttonAction);

        bPaste = new JButton("Paste");
        bPaste.addActionListener(buttonAction);

        navigation.add(bPrev); navigation.add(bNext);
        navigation.add(bNewFile); navigation.add(bOpenFile);
        navigation.add(bDeleteFile); navigation.add(bCopy); navigation.add(bPaste);

//  Tree
        tree = new JTree();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(root);

        // Show folder in root
        File[] fileRoot = fileSystemView.getRoots();
        for(File file : fileRoot) {
            if(file.isDirectory()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(file);
                root.add(node);

                File[] fileNode = fileSystemView.getFiles(file, true);
                for(File ffile: fileNode) {
                    if(ffile.isDirectory()) node.add(new DefaultMutableTreeNode(ffile));
                }
            }
        }

        // Tree listener
        TreeSelectionListener treeSelectionListener = e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.getPath().getLastPathComponent();
            showChildrenInTree(node);
            setSelectedDirectory((File) node.getUserObject());
        };

        tree.setModel(treeModel);
        tree.addTreeSelectionListener(treeSelectionListener);
        tree.setCellRenderer(new FileTreeCellRenderer());
        tree.setRootVisible(false);
        tree.expandRow(0);

        treeScroll = new JScrollPane(tree);
        Dimension treeSize = new Dimension((int) (WIDTH * 0.4), (int) treeScroll.getPreferredSize().getHeight());
        treeScroll.setPreferredSize(treeSize);

//  Table
        table = new JTable();

        tableModelListener = e -> {
            int row = table.getSelectionModel().getLeadSelectionIndex();
        };
        MouseListener tableMouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                File file = ((FileTableModel) table.getModel()).getFile(table.getSelectedRow());
                selectedFileInTable = file;
                if(e.getClickCount() == 2) {
                    if(file.isDirectory()) {
                        openDirectoryInTable(file);
                    } else {
                        openFile();
                    }
                }
            }
        };

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(tableMouseListener);
        table.setAutoCreateRowSorter(true);
        table.setShowVerticalLines(false);

        tableScroll = new JScrollPane(table);
        Dimension tableSize = new Dimension((int) (HEIGHT * 0.6), (int) tableScroll.getPreferredSize().getHeight());
        table.setPreferredSize(new Dimension(tableSize));

        add(navigation, BorderLayout.NORTH);
        add(treeScroll, BorderLayout.WEST);
        add(tableScroll, BorderLayout.EAST);
    }

    private void openDirectoryInTable(File file) {
        System.out.println(file);
        tree.setSelectionRow(-1);
        selectedDirectory = null;
        selectedFileInTable = null;
        setTableData(file);
    }

    private void setSelectedDirectory(File file) {
        selectedDirectory = file;
        selectedFileInTable = null;
        table.getSelectionModel().setLeadSelectionIndex(-1);
        setTableData(file);
    }

    private void setTableData(File file) {
        SwingUtilities.invokeLater(() -> {
            currentDirectory = file;
            if(!fileSystemView.isRoot(file.getAbsoluteFile())) history.add(file.getAbsolutePath());
            if(tableModel == null) {
                tableModel = new FileTableModel(file);
                table.setModel(tableModel);
          }
            table.getSelectionModel().removeListSelectionListener(tableModelListener);
            tableModel.setFiles(fileSystemView.getFiles(file, true));
            table.getSelectionModel().addListSelectionListener(tableModelListener);
            table.setRowHeight(25);
            table.getColumnModel().getColumn(0).setPreferredWidth(5);
        });
    }

    public void showRoot() {
        tree.setSelectionInterval(0, 0);
    }

    private void showChildrenInTree(DefaultMutableTreeNode node) {
        tree.setEnabled(false);

        SwingWorker<Void, File> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                File file = (File) node.getUserObject();
                if (file.isDirectory()) {
                    File[] files = fileSystemView.getFiles(file, true);
                    if (node.isLeaf()) {
                        for (File f : files) {
                            if (f.isDirectory()) {
                                publish(f);
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<File> file) {
                for (File f : file) {
                    node.add(new DefaultMutableTreeNode(f));
                }
            }

            @Override
            protected void done() {
                tree.setEnabled(true);
            }
        };
        worker.execute();
    }

    private void openFile() {
        if(selectedFileInTable == null) {
            showError("Choose file to open");
            return;
        }
        try {
            desktop.open(selectedFileInTable);
        } catch (IOException ex) {
//            ex.printStackTrace();
            showError("Cannot this file");
        }
    }

    private void showError(String content) {
        JOptionPane.showMessageDialog(this, content);
    }

    private void newFile(String fileName) {
        try {
            File file = fileSystemView.createFileObject(currentDirectory, fileName);
            file.createNewFile();
            setTableData(currentDirectory);
        } catch (IOException e) {
            showError("Cannot create new file");
        }
    }

    private void gotoPrevFile() {
        int id = history.indexOf(currentDirectory.toString()) - 1;
        if(id < 0) {
            showError("Have not previous file");
            return;
        }
        String prevFilename = history.get(id);
        File file = new File(prevFilename);
        setTableData(file);
    }

    private void gotoNextFile() {
        int id = history.indexOf(currentDirectory.toString()) + 1;
        if(id >= history.size()) {
            showError("Have not next file");
            return;
        }
        String nextFilename = history.get(id);
        File file = new File(nextFilename);
        setTableData(file);
    }

    void deleteFile() {
        System.out.println(selectedFileInTable);
        if(selectedFileInTable == null) {
            showError("Have no file selected");
            return;
        }
        if(JOptionPane.showConfirmDialog(this, "Are you sure ?") == JOptionPane.OK_OPTION) {
            if(selectedFileInTable.delete()) {
                setTableData(currentDirectory);
            } else {
                showError("Cannot delete file");
            }
        }
    }

    void copyFile() {
        if(selectedFileInTable == null) {
            showError("Have no file selected");
            return;
        }
        copiedFile = selectedFileInTable;
        showError("Copy successful");
    }

    void pasteFile() {
        if(copiedFile == null) {
            showError("Have no file copied");
            return;
        }
        try {
            Files.copy(copiedFile.toPath(), fileSystemView.createFileObject(currentDirectory,  copiedFile.getName()).toPath());
            setTableData(currentDirectory);
            showError("Paste successful");
        } catch (IOException e) {
            e.printStackTrace();
        }
//        fileSystemView.createFileObject(currentDirectory, copiedFile.getName());
//        setTableData(currentDirectory);
    }

    class ButtonAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if(e.getActionCommand().equals("New")) {
                String fileName = JOptionPane.showInputDialog("Enter file name: ");
                newFile(fileName);
            } else if(e.getActionCommand().equals("Open")) {
                openFile();
            } else if(e.getActionCommand().equals("Delete")) {
                deleteFile();
            } else if(e.getActionCommand().equals("Prev")) {
                gotoPrevFile();
            } else if(e.getActionCommand().equals("Next")) {
                gotoNextFile();
            } else if(e.getActionCommand().equals("Copy")) {
                copyFile();
            } else if(e.getActionCommand().equals("Paste")) {
                pasteFile();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Explorer");

            FileExplorer fileExplorer = new FileExplorer();
            frame.setContentPane(fileExplorer);

            frame.setVisible(true);
            frame.pack();
            frame.setMinimumSize(new Dimension(WIDTH, HEIGHT));
//                frame.setLocationByPlatform(true);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            fileExplorer.showRoot();
        });
    }

}

class FileTreeCellRenderer extends DefaultTreeCellRenderer {

    final private FileSystemView fileSystemView;

    private final JLabel label;

    FileTreeCellRenderer() {
        label = new JLabel();
        label.setOpaque(true);
        fileSystemView = FileSystemView.getFileSystemView();
    }

    @Override
    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {

//        return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        File file = (File)node.getUserObject();
        if(file == null) return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        label.setIcon(fileSystemView.getSystemIcon(file));
        label.setText(fileSystemView.getSystemDisplayName(file));
        label.setToolTipText(file.getPath());

        if (selected) {
            label.setBackground(backgroundSelectionColor);
            label.setForeground(textSelectionColor);
        } else {
            label.setBackground(backgroundNonSelectionColor);
            label.setForeground(textNonSelectionColor);
        }

        return label;
    }
}

class FileTableModel extends AbstractTableModel {

    File fileRoot;
    FileSystemView  fileSystemView = FileSystemView.getFileSystemView();
    File[] files;
    String[] columns = {
        "Icon",
        "File",
        "Path/name",
        "Last Modified",
    };

    FileTableModel(File file) {
        fileRoot = file;
        files = fileSystemView.getFiles(fileRoot, true);
    }
    FileTableModel(File[] files) {
        this.files = files;
    }

    @Override
    public int getRowCount() {
        return files.length;
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    public File getFile(int row) { return files[row]; }

    public void setFiles(File[] files) {
        this.files = files;
        fireTableDataChanged();
    }

    @Override
    public Object getValueAt(int row, int column) {
        File file = files[row];
        return switch (column) {
            case 0 -> fileSystemView.getSystemIcon(file);
            case 1 -> fileSystemView.getSystemDisplayName(file);
            case 2 -> file.getAbsolutePath();
            case 3 -> file.lastModified();
            default -> "";
        };
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 0 -> ImageIcon.class;
            case 1 -> String.class;
            case 2 -> String.class;
            case 3 -> Date.class;
            default -> String.class;
        };
    }
}