package apicollector.ui;

import apicollector.burp.*;
import apicollector.core.*;
import apicollector.export.*;
import apicollector.ingest.ImportDispatcher;
import apicollector.ingest.ImportedEndpoint;
import apicollector.model.Endpoint;
import apicollector.model.EndpointShape;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

/**
 * Single-page UI: a collapsible Scope + settings strip at the top, then the working area
 * (coverage + captured values). Layout of the working area is chosen by {@link Layout} so
 * the two arrangements can be compared. Refresh is driven by CaptureStore.version().
 */
public final class ApiCollectorTab {
    public enum Layout { VERTICAL_SPLIT, VALUES_TAB }

    private final MontoyaApi api;
    private final CaptureStore store;
    private final ScopeController scope;
    private final ObservationFactory factory;
    private final LiveHttpHandler handler;
    private final ImportDispatcher dispatcher = new ImportDispatcher();
    private final Layout layout;

    private final CoverageModel coverageModel = new CoverageModel();
    private final KnownValuesModel kvModel = new KnownValuesModel();
    private final JPanel root = new JPanel(new BorderLayout(6, 6));
    private final JLabel status = new JLabel();

    private final JComboBox<String> formatBox = new JComboBox<>(new String[]{"Postman v2.1", "Bruno"});
    private final JTextField placeholderField = new JTextField("{{name}}", 10);
    private final JTextField collectionNameField = new JTextField("Client API", 14);
    private final JTextField baseUrlField = new JTextField("https://api.example.com", 20);
    private final JTextArea hostArea = new JTextArea(4, 24);
    // auto-fill (live) settings — deliberately separate from the export placeholder format
    private final JTextField matchFormatsField = new JTextField("{{name}}", 14);
    private final JCheckBox preferLatestBox = new JCheckBox("Auto-refresh from same call", true);
    private final JCheckBox skipStaticBox = new JCheckBox("Ignore static assets", true);
    private final Map<burp.api.montoya.core.ToolType, JCheckBox> toolBoxes = new LinkedHashMap<>();

    private long lastVersion = -1;

    public ApiCollectorTab(MontoyaApi api, CaptureStore store, ScopeController scope,
                           ObservationFactory factory, LiveHttpHandler handler, Layout layout) {
        this.api = api; this.store = store; this.scope = scope;
        this.factory = factory; this.handler = handler; this.layout = layout;

        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(topStrip(), BorderLayout.NORTH);
        root.add(workingArea(), BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);

        new javax.swing.Timer(1000, e -> refreshIfChanged()).start();
    }

    public Component component() { return root; }

    private void refreshIfChanged() {
        long v = store.version();
        if (v != lastVersion) { lastVersion = v; coverageModel.reload(); kvModel.reload(); }
        status.setText(statusText());
    }
    private String statusText() {
        int total = store.coverage().expectedCount();
        int seen = store.coverage().seenExpected().size();
        int pct = total == 0 ? 0 : (int) Math.round(100.0 * seen / total);
        return "  Coverage " + seen + "/" + total + " (" + pct + "%)   \u00b7   "
             + store.endpoints().size() + " endpoints captured   \u00b7   "
             + store.params().names().size() + " captured values   \u00b7   capture "
             + (handler.isEnabled() ? "ON" : "OFF")
             + (handler.isSubstituteLive() ? "   \u00b7   auto-fill LIVE" : "")
             + collectionStatus();
    }

    /** Push the placeholder format and base URL into the live handler. */
    private void syncHandlerSettings() {
        var styles = PlaceholderFormats.parse(matchFormatsField.getText());
        if (styles.isEmpty()) styles = List.of(styleFrom(placeholderField.getText()));
        handler.setPlaceholderStyles(styles);
        handler.setBaseUrlFallback(baseUrlField.getText());
        handler.setSkipStatic(skipStaticBox.isSelected());
        store.params().setAutoRefresh(preferLatestBox.isSelected());
        Set<burp.api.montoya.core.ToolType> on = new LinkedHashSet<>();
        toolBoxes.forEach((t, cb) -> { if (cb.isSelected()) on.add(t); });
        if (!on.isEmpty()) handler.setTools(on);
    }

    private String collectionStatus() {
        var lc = store.collection();
        if (lc == null) return "";
        int total = lc.distinctPlaceholders();
        int done = lc.resolvedCount(store.params());
        return "   \u00b7   collection " + lc.fileName() + ": " + done + "/" + total + " placeholders resolved";
    }

    // ---------------- top strip: toolbar + collapsible scope/settings ----------------
    private JComponent topStrip() {
        JPanel strip = new JPanel(new BorderLayout());
        strip.add(toolbar(), BorderLayout.NORTH);

        JPanel config = new JPanel(new GridLayout(1, 3, 10, 0));
        config.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        config.add(scopePanel());
        config.add(autoFillPanel());
        config.add(exportPanel());

        // Collapsible so the working area gets full height once configured.
        JPanel collapsible = new JPanel(new BorderLayout());
        JToggleButton toggle = new JToggleButton("\u25be  Scope & export settings", true);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setBorderPainted(false);
        toggle.addActionListener(e -> {
            boolean open = toggle.isSelected();
            config.setVisible(open);
            toggle.setText((open ? "\u25be" : "\u25b8") + "  Scope & export settings");
        });
        collapsible.add(toggle, BorderLayout.NORTH);
        collapsible.add(config, BorderLayout.CENTER);
        strip.add(collapsible, BorderLayout.CENTER);
        return strip;
    }

    private JComponent toolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JToggleButton capture = new JToggleButton("Capture ON", true);
        capture.addActionListener(e -> {
            handler.setEnabled(capture.isSelected());
            capture.setText(capture.isSelected() ? "Capture ON" : "Capture OFF");
            status.setText(statusText());
        });
        bar.add(capture);

        JToggleButton subst = new JToggleButton("Auto-fill live requests", false);
        subst.setToolTipText("API-only testing: replace placeholders in outgoing requests "
            + "with captured values (e.g. {{orderId}} from the create-order response)");
        subst.addActionListener(e -> {
            handler.setSubstituteLive(subst.isSelected());
            syncHandlerSettings();
            status.setText(statusText());
        });
        bar.add(subst);
        bar.addSeparator();

        JButton importBtn = new JButton("Import \u25be");
        importBtn.setToolTipText("OpenAPI/Swagger (JSON or YAML), Postman, Bruno, Excel, CSV, or pasted text. "
            + "Endpoints go to coverage; any placeholders make it the fill target.");
        importBtn.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            JMenuItem file = new JMenuItem("From file or folder...");
            file.addActionListener(x -> doImport());
            JMenuItem paste = new JMenuItem("Paste text...");
            paste.addActionListener(x -> doImportPaste());
            m.add(file); m.add(paste);
            m.show(importBtn, 0, importBtn.getHeight());
        });
        bar.add(importBtn);


        JButton history = new JButton("Rescan history");
        history.setToolTipText("Traffic captures automatically; re-scan after changing scope");
        history.addActionListener(e -> {
            int n = new ProxyHistoryImporter().importInto(store, api, scope, factory);
            refreshIfChanged();
            JOptionPane.showMessageDialog(root, "Captured " + n + " in-scope items from proxy history.");
        });
        bar.add(history);
        bar.addSeparator();

        JButton export = new JButton("Export \u25be");
        export.addActionListener(e -> exportMenu().show(export, 0, export.getHeight()));
        bar.add(export);

        JButton clear = new JButton("Clear \u25be");
        clear.addActionListener(e -> clearMenu().show(clear, 0, clear.getHeight()));
        bar.add(clear);
        return bar;
    }

    private JComponent scopePanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Scope"));

        JRadioButton burp = new JRadioButton("Use Burp target scope", true);
        JRadioButton custom = new JRadioButton("Custom hosts (one per line, subdomains match)");
        ButtonGroup g = new ButtonGroup(); g.add(burp); g.add(custom);

        hostArea.setEnabled(false);
        hostArea.setToolTipText("e.g.\napi.client.com\nlocalhost\napp.client.com");
        JScrollPane hostScroll = new JScrollPane(hostArea);

        burp.addActionListener(e -> { scope.setMode(ScopeController.Mode.BURP); hostArea.setEnabled(false); });
        custom.addActionListener(e -> { scope.setMode(ScopeController.Mode.CUSTOM); hostArea.setEnabled(true); hostArea.requestFocusInWindow(); });
        hostArea.getDocument().addDocumentListener(new SimpleDoc(() -> scope.setHosts(hostArea.getText())));

        JPanel radios = new JPanel(new GridLayout(0, 1));
        radios.add(burp); radios.add(custom);
        p.add(radios, BorderLayout.NORTH);
        p.add(hostScroll, BorderLayout.CENTER);
        return p;
    }

    private JComponent autoFillPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Auto-fill live requests"));

        JPanel top = new JPanel(new BorderLayout(4, 2));
        JLabel l = new JLabel("Match formats");
        matchFormatsField.setToolTipText("Placeholder formats to look for in outgoing requests. "
            + "Comma-separate several, e.g.  {{name}}, :name, ${name}  \u2014 the client's collection "
            + "may not use the same format you export.");
        top.add(l, BorderLayout.WEST);
        top.add(matchFormatsField, BorderLayout.CENTER);

        JPanel opts = new JPanel(new GridLayout(0, 1));
        preferLatestBox.setToolTipText("If the SAME call returns a new value (e.g. re-login gives a fresh "
            + "token), update the active value. A same-named field from a different call never overrides it. "
            + "Values you pick by hand stay pinned.");
        skipStaticBox.setToolTipText("Don't capture values from .js/.css/image responses");
        preferLatestBox.addActionListener(e -> syncHandlerSettings());
        skipStaticBox.addActionListener(e -> syncHandlerSettings());
        opts.add(preferLatestBox);
        opts.add(skipStaticBox);

        JPanel tools = new JPanel(new GridLayout(0, 2));
        tools.setBorder(BorderFactory.createTitledBorder("Apply to"));
        for (var t : List.of(burp.api.montoya.core.ToolType.PROXY, burp.api.montoya.core.ToolType.REPEATER,
                             burp.api.montoya.core.ToolType.INTRUDER, burp.api.montoya.core.ToolType.SCANNER)) {
            boolean on = t != burp.api.montoya.core.ToolType.SCANNER;
            JCheckBox cb = new JCheckBox(t.toolName(), on);
            cb.addActionListener(e -> syncHandlerSettings());
            toolBoxes.put(t, cb);
            tools.add(cb);
        }

        JPanel body = new JPanel(new BorderLayout(4, 4));
        body.add(opts, BorderLayout.NORTH);
        body.add(tools, BorderLayout.CENTER);
        p.add(top, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JComponent exportPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("Export settings"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 3, 2, 3); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        addRow(p, c, y++, "Format", formatBox);
        addRow(p, c, y++, "Placeholder", placeholderField);
        addRow(p, c, y++, "Collection name", collectionNameField);
        addRow(p, c, y++, "Base URL", baseUrlField);
        return p;
    }
    /** Fix a column's width so narrow numeric columns don't hog space. */
    private static void setWidth(JTable t, int col, int pref, int max) {
        var tc = t.getColumnModel().getColumn(col);
        tc.setPreferredWidth(pref);
        tc.setMaxWidth(max);
    }

    private void addRow(JPanel p, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridx = 0; c.gridy = y; c.weightx = 0; p.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; p.add(field, c);
    }

    // ---------------- working area (two layouts) ----------------
    private JComponent workingArea() {
        return layout == Layout.VERTICAL_SPLIT ? verticalSplit() : valuesInTab();
    }
    private JComponent verticalSplit() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, coveragePane(), knownValuesPane());
        sp.setResizeWeight(0.62);
        return sp;
    }
    private JComponent valuesInTab() {
        JTabbedPane t = new JTabbedPane();
        t.addTab("Coverage", coveragePane());
        t.addTab("Captured values", knownValuesPane());
        return t;
    }

    private JComponent coveragePane() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JTable table = new JTable(coverageModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setAutoCreateRowSorter(true);
        TableRowSorter<CoverageModel> sorter = new TableRowSorter<>(coverageModel);
        table.setRowSorter(sorter);
        setWidth(table, 0, 70, 90);    // Method
        setWidth(table, 2, 80, 90);    // Status
        setWidth(table, 3, 55, 70);    // Hits

        JTextField filter = new JTextField();
        filter.getDocument().addDocumentListener(new SimpleDoc(() -> {
            String q = filter.getText().trim();
            sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q)));
        }));
        JComboBox<String> show = new JComboBox<>(new String[]{"All", "Not seen", "Seen"});
        show.addActionListener(e -> {
            int i = show.getSelectedIndex();
            coverageModel.statusFilter = i == 0 ? null : (i == 1 ? "not seen" : "seen");
            coverageModel.reload();
        });

        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(new JLabel("Coverage "), BorderLayout.WEST);
        north.add(filter, BorderLayout.CENTER);
        north.add(show, BorderLayout.EAST);
        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JComponent knownValuesPane() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JTable table = new JTable(kvModel);
        TableRowSorter<KnownValuesModel> sorter = new TableRowSorter<>(kvModel);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(1).setCellEditor(new ValueEditor());
        setWidth(table, 0, 140, 240);  // Parameter
        setWidth(table, 2, 50, 60);    // Count

        // search box: filters across name + value + sources
        JTextField search = new JTextField();
        search.setToolTipText("Values pulled from requests and responses as you test. "
            + "The selected value for each name is what gets used to fill placeholders and the environment file.");
        search.getDocument().addDocumentListener(new SimpleDoc(() -> {
            String q = search.getText().trim();
            sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(q)));
        }));
        JComboBox<String> showKv = new JComboBox<>(new String[]{"All", "Waiting", "Captured"});
        showKv.setToolTipText("Waiting = placeholders from the imported collection with no value captured yet");
        showKv.addActionListener(e -> {
            int i = showKv.getSelectedIndex();
            kvModel.rowFilter = i == 0 ? null : (i == 1 ? "waiting" : "captured");
            kvModel.reload();
        });

        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(new JLabel("Captured values "), BorderLayout.WEST);
        north.add(search, BorderLayout.CENTER);
        north.add(showKv, BorderLayout.EAST);

        // copy: double-click copies the value; right-click offers copy variants
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int vr = table.rowAtPoint(e.getPoint());
                    if (vr >= 0) copyValue(table, vr);
                }
            }
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int vr = table.rowAtPoint(e.getPoint());
                if (vr >= 0) { table.setRowSelectionInterval(vr, vr); kvPopup(table, vr).show(table, e.getX(), e.getY()); }
            }
        });

        p.add(north, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private String[] nameValueAt(JTable table, int viewRow) {
        int mr = table.convertRowIndexToModel(viewRow);
        String name = kvModel.nameAt(mr);
        String value = store.params().active(name);
        return new String[]{name, value == null ? "" : value};
    }
    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
    private void copyValue(JTable table, int viewRow) {
        String[] nv = nameValueAt(table, viewRow);
        copyToClipboard(nv[1]);
        status.setText("  Copied value of " + nv[0] + " to clipboard");
    }
    private JPopupMenu kvPopup(JTable table, int viewRow) {
        String[] nv = nameValueAt(table, viewRow);
        String ph = styleFrom(placeholderField.getText()).wrap(nv[0]);
        JPopupMenu m = new JPopupMenu();
        addCopy(m, "Copy value", nv[1]);
        addCopy(m, "Copy placeholder  " + ph, ph);
        addCopy(m, "Copy name", nv[0]);
        addCopy(m, "Copy  name=value", nv[0] + "=" + nv[1]);
        m.addSeparator();
        addCopy(m, "Copy all values as JSON", allValuesJson());
        return m;
    }
    private void addCopy(JPopupMenu m, String label, String payload) {
        JMenuItem it = new JMenuItem(label);
        it.addActionListener(e -> { copyToClipboard(payload); status.setText("  Copied to clipboard"); });
        m.add(it);
    }
    private String allValuesJson() {
        StringBuilder sb = new StringBuilder("{\n");
        var snap = store.params().activeSnapshot();
        int i = 0;
        for (var e : snap.entrySet()) {
            if (i++ > 0) sb.append(",\n");
            sb.append("  \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"");
        }
        return sb.append("\n}").toString();
    }

    // ---------------- menus & actions ----------------
    private JPopupMenu exportMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem full = new JMenuItem("New collection + environment...");
        full.setToolTipText("Build a collection from the traffic you captured");
        full.addActionListener(e -> doExport(true));
        JMenuItem collOnly = new JMenuItem("New collection only...");
        collOnly.addActionListener(e -> doExport(false));
        m.add(full); m.add(collOnly);

        var lc = store.collection();
        JMenuItem filled = new JMenuItem(lc == null
            ? "Filled client collection... (none imported)"
            : "Filled client collection (" + lc.fileName() + ")...");
        filled.setToolTipText("Save the collection you imported, with placeholders replaced by captured values");
        filled.setEnabled(lc != null);
        filled.addActionListener(e -> doSaveFilled());
        m.addSeparator(); m.add(filled);

        JMenuItem ns = new JMenuItem("Not-seen list (client deliverable)...");
        ns.addActionListener(e -> saveText(deliverableName("not-seen") + ".txt", notSeenText()));
        m.addSeparator(); m.add(ns);
        return m;
    }
    private JPopupMenu clearMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem cap = new JMenuItem("Clear captured traffic");
        cap.addActionListener(e -> { if (confirm("Clear captured endpoints and captured values?\nImported endpoint list is kept.")) { store.clearCaptured(); refreshIfChanged(); } });
        JMenuItem exp = new JMenuItem("Clear imported endpoint list");
        exp.addActionListener(e -> { if (confirm("Clear the imported endpoint list?\nCaptured traffic is kept.")) { store.clearExpected(); refreshIfChanged(); } });
        var lc = store.collection();
        JMenuItem unload = new JMenuItem(lc == null ? "Unload client collection" : "Unload \"" + lc.fileName() + "\"");
        unload.setEnabled(lc != null);
        unload.addActionListener(e -> { store.unloadCollection(); refreshIfChanged(); });

        JMenuItem all = new JMenuItem("Reset everything");
        all.addActionListener(e -> {
            if (confirm("Reset everything \u2014 captured traffic, imported endpoints,\nand the loaded client collection?")) {
                store.clearAll(); refreshIfChanged();
            }
        });
        m.add(cap); m.add(exp); m.add(unload); m.addSeparator(); m.add(all);
        return m;
    }
    private boolean confirm(String msg) {
        return JOptionPane.showConfirmDialog(root, msg, "Confirm", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
    private void doExport(boolean includeEnv) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            PlaceholderStyle style = styleFrom(placeholderField.getText());
            CollectionBuilder cb = new CollectionBuilder(style, "baseUrl");
            apicollector.export.Collection col = cb.build(collectionNameField.getText(), store);
            EnvironmentModel env = includeEnv ? cb.environment("Local", baseUrlField.getText(), store) : null;
            CollectionExporter exporter = formatBox.getSelectedIndex() == 0 ? new PostmanExporter() : new BrunoExporter();
            String base = deliverableName(collectionNameField.getText());
            new CollectionWriter().write(fc.getSelectedFile().toPath(), exporter.export(col, env, base));
            JOptionPane.showMessageDialog(root,
                "Exported " + formatBox.getSelectedItem()
                + (includeEnv ? " (collection + environment)" : " (collection only)")
                + "\nBase name: " + base + "\nto " + fc.getSelectedFile());
        } catch (Exception ex) { error(ex); }
    }

    /** One import: adds endpoints to coverage AND, if the file has placeholders, loads it as the fill target. */
    private void doImport() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import endpoint list or collection");
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES); // a Bruno collection is a folder
        if (fc.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path f = fc.getSelectedFile().toPath();
        try {
            List<ImportedEndpoint> eps = dispatcher.importFile(f);
            for (ImportedEndpoint e : eps) store.importExpected(e.method, e.path);

            LoadedCollection lc = null;
            if (!java.nio.file.Files.isDirectory(f) && !isBinary(f)) {
                String text = java.nio.file.Files.readString(f);
                for (PlaceholderStyle style : matchStyles()) {
                    LoadedCollection candidate = new LoadedCollection(f.getFileName().toString(), text, style);
                    if (candidate.distinctPlaceholders() > 0) { lc = candidate; break; }
                }
            }
            if (lc != null) store.loadCollection(lc);
            refreshIfChanged();

            StringBuilder msg = new StringBuilder();
            msg.append(eps.isEmpty() ? "No endpoints found."
                     : "Added " + eps.size() + " endpoint(s) to coverage.");
            if (lc != null) {
                msg.append("\n\nFound ").append(lc.distinctPlaceholders())
                   .append(" placeholder(s) \u2014 loaded as the fill target.\n")
                   .append(lc.resolvedCount(store.params())).append(" already have captured values; ")
                   .append("the rest show as \"waiting\" and fill in as you test.");
            } else if (java.nio.file.Files.isDirectory(f)) {
                msg.append("\n\n(Folder imported for coverage. To fill placeholders, import a single file.)");
            }
            JOptionPane.showMessageDialog(root, msg.toString(), "Import complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (UnsupportedOperationException ux) {
            JOptionPane.showMessageDialog(root, ux.getMessage(), "Decoder needed", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) { error(ex); }
    }

    private void doImportPaste() {
        JTextArea area = new JTextArea(10, 50);
        if (JOptionPane.showConfirmDialog(root, new JScrollPane(area), "Paste endpoint list",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        List<ImportedEndpoint> eps = dispatcher.importText("paste.txt", area.getText());
        for (ImportedEndpoint e : eps) store.importExpected(e.method, e.path);
        refreshIfChanged();
        JOptionPane.showMessageDialog(root, "Added " + eps.size() + " endpoint(s) to coverage.");
    }

    private List<PlaceholderStyle> matchStyles() {
        var styles = PlaceholderFormats.parse(matchFormatsField.getText());
        return styles.isEmpty() ? List.of(styleFrom(placeholderField.getText())) : styles;
    }
    private static boolean isBinary(java.nio.file.Path f) {
        String n = f.getFileName().toString().toLowerCase();
        return n.endsWith(".xlsx") || n.endsWith(".xls");
    }

    private void doSaveFilled() {
        var lc = store.collection();
        if (lc == null) return;
        try {
            java.util.Map<String, String> values = new java.util.LinkedHashMap<>(store.params().activeSnapshot());
            values.putIfAbsent("baseUrl", baseUrlField.getText());
            PlaceholderHydrator.Result r = new PlaceholderHydrator().hydrate(lc.text(), lc.style(), values);

            JFileChooser out = new JFileChooser();
            out.setDialogTitle("Save filled collection");
            out.setSelectedFile(new java.io.File(deliverableName("filled-" + lc.fileName())));
            if (out.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
            java.nio.file.Files.writeString(out.getSelectedFile().toPath(), r.text);

            String msg = "Filled " + r.replacements + " placeholder occurrence(s).";
            if (!r.unresolved.isEmpty())
                msg += "\n\nStill no value for (left as placeholders):\n  " + String.join(", ", r.unresolved);
            JOptionPane.showMessageDialog(root, msg,
                r.unresolved.isEmpty() ? "Saved" : "Saved \u2014 some placeholders unresolved",
                r.unresolved.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) { error(ex); }
    }


    private void showMissing() {
        var lc = store.collection();
        if (lc == null) {
            JOptionPane.showMessageDialog(root,
                "No client collection imported yet.\n\nUse Import \u25be to load one \u2014 its placeholders\n"
                + "will be tracked here as you capture values.");
            return;
        }
        java.util.List<String> miss = lc.unresolved(store.params());
        JOptionPane.showMessageDialog(root, miss.isEmpty()
            ? "All placeholders in " + lc.fileName() + " have values."
            : "Still need values for:\n\n  " + String.join("\n  ", miss)
              + "\n\nTrigger the endpoints that return these, and they fill in automatically.");
    }

    /** Deliverable base name: <burp project>_<exportName>, sanitized. Project name auto from Burp. */
    private String deliverableName(String exportName) {
        String project = "";
        try { if (api != null && api.project() != null) project = api.project().name(); } catch (Exception ignore) {}
        String ex = exportName == null || exportName.isBlank() ? "export" : exportName;
        String combined = (project == null || project.isBlank() ? "" : project + "_") + ex;
        return combined.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }
    private static PlaceholderStyle styleFrom(String pattern) {
        int i = pattern.indexOf("name");
        return i < 0 ? PlaceholderStyle.DOUBLE_CURLY : new PlaceholderStyle(pattern.substring(0, i), pattern.substring(i + 4));
    }
    private String notSeenText() {
        StringBuilder sb = new StringBuilder("Endpoints not triggered from the UI:\n\n");
        for (EndpointShape s : store.coverage().notSeen()) sb.append(s.method()).append(' ').append(s.displayTemplate()).append('\n');
        return sb.toString();
    }
    private void saveText(String suggested, String text) {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new java.io.File(suggested));
        if (fc.showSaveDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try { Files.writeString(fc.getSelectedFile().toPath(), text); } catch (Exception ex) { error(ex); }
    }
    private void error(Exception ex) {
        if (api != null) api.logging().logToError("API Collector UI error", ex);
        JOptionPane.showMessageDialog(root, String.valueOf(ex.getMessage()), "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------- models ----------------
    private final class CoverageModel extends AbstractTableModel {
        private final String[] cols = {"Method", "Endpoint", "Status", "Hits"};
        private List<Object[]> rows = new ArrayList<>();
        String statusFilter;
        void reload() {
            List<Object[]> r = new ArrayList<>();
            Map<EndpointShape, Integer> hits = new HashMap<>();
            for (Endpoint ep : store.endpoints()) hits.put(ep.shape, ep.count);
            if (statusFilter == null || statusFilter.equals("seen"))
                for (EndpointShape s : store.coverage().seenExpected())
                    r.add(new Object[]{s.method(), s.displayTemplate(), "seen", hits.getOrDefault(s, 0)});
            if (statusFilter == null || statusFilter.equals("not seen"))
                for (EndpointShape s : store.coverage().notSeen())
                    r.add(new Object[]{s.method(), s.displayTemplate(), "not seen", 0});
            rows = r; fireTableDataChanged();
        }
        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Class<?> getColumnClass(int c) { return c == 3 ? Integer.class : String.class; }
        public Object getValueAt(int r, int c) { return rows.get(r)[c]; }
    }
    private final class KnownValuesModel extends AbstractTableModel {
        private final String[] cols = {"Parameter", "Active value", "Count", "Sources"};
        private List<String> names = new ArrayList<>();
        String rowFilter;   // null = all, "waiting" = no value yet, "captured" = has a value
        void reload() {
            List<String> all = store.params().names();
            if (rowFilter == null) { names = all; }
            else {
                List<String> keep = new ArrayList<>();
                for (String n : all) {
                    boolean waiting = store.params().active(n) == null;
                    if (waiting == rowFilter.equals("waiting")) keep.add(n);
                }
                names = keep;
            }
            fireTableDataChanged();
        }
        public int getRowCount() { return names.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Class<?> getColumnClass(int c) { return c == 2 ? Integer.class : String.class; }
        public boolean isCellEditable(int r, int c) { return c == 1; }
        public Object getValueAt(int r, int c) {
            String n = names.get(r); var vals = store.params().valuesOf(n);
            return switch (c) {
                case 0 -> n;
                case 1 -> store.params().active(n);
                case 2 -> vals.size();
                default -> { Set<String> src = new LinkedHashSet<>(); for (var v : vals) src.addAll(v.sources); yield String.join(", ", src); }
            };
        }
        public void setValueAt(Object val, int r, int c) { if (c == 1) store.params().setActive(names.get(r), String.valueOf(val)); }
        String nameAt(int modelRow) { return names.get(modelRow); }
    }
    private final class ValueEditor extends DefaultCellEditor {
        ValueEditor() { super(new JComboBox<String>()); }
        @Override public Component getTableCellEditorComponent(JTable t, Object v, boolean sel, int viewRow, int col) {
            @SuppressWarnings("unchecked") JComboBox<String> box = (JComboBox<String>) getComponent();
            box.removeAllItems();
            String name = ((KnownValuesModel) t.getModel()).nameAt(t.convertRowIndexToModel(viewRow));
            for (var val : store.params().valuesOf(name)) box.addItem(val.value);
            box.setSelectedItem(v);
            return box;
        }
    }
    private static final class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable r; SimpleDoc(Runnable r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}
