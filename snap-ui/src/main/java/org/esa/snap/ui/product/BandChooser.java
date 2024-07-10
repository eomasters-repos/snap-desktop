package org.esa.snap.ui.product;

import com.bc.ceres.swing.TableLayout;
import eu.esa.snap.core.datamodel.group.BandGroup;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.ui.ModalDialog;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * A dialog which lets the user select from a product's bands and tie-point grids.
 */
public class BandChooser extends ModalDialog implements LoadSaveRasterDataNodesConfigurationsComponent {

    private final boolean selectAtLeastOneBand;
    private BandChoosingStrategy strategy;
    private boolean addLoadSaveConfigurationButtons;

    public BandChooser(Window parent, String title, String helpID,
                       Band[] allBands, Band[] selectedBands, BandGroup autoGrouping,
                       boolean addLoadSaveConfigurationButtons) {
        super(parent, title, ModalDialog.ID_OK_CANCEL, helpID);
        this.addLoadSaveConfigurationButtons = addLoadSaveConfigurationButtons;
        boolean multipleProducts = bandsAndGridsFromMoreThanOneProduct(allBands, null);
        strategy = new GroupedBandChoosingStrategy(allBands, selectedBands, null, null, autoGrouping, multipleProducts);
        selectAtLeastOneBand = false;
        initUI();
    }

    public BandChooser(Window parent, String title, String helpID,
                       Band[] allBands, Band[] selectedBands, boolean addLoadSaveConfigurationButtons) {
        this(parent, title, helpID, true, allBands, selectedBands, null, null, addLoadSaveConfigurationButtons);
    }

    public BandChooser(Window parent, String title, String helpID, boolean selectAtLeastOneBand,
                       Band[] allBands, Band[] selectedBands,
                       TiePointGrid[] allTiePointGrids, TiePointGrid[] selectedTiePointGrids,
                       boolean addLoadSaveConfigurationButtons) {
        super(parent, title, ModalDialog.ID_OK_CANCEL, helpID);
        this.addLoadSaveConfigurationButtons = addLoadSaveConfigurationButtons;
        boolean multipleProducts = bandsAndGridsFromMoreThanOneProduct(allBands, allTiePointGrids);
        strategy = new DefaultBandChoosingStrategy(allBands, selectedBands, allTiePointGrids, selectedTiePointGrids,
                                                   multipleProducts);
        this.selectAtLeastOneBand = selectAtLeastOneBand;
        initUI();
    }

    private boolean bandsAndGridsFromMoreThanOneProduct(Band[] allBands, TiePointGrid[] allTiePointGrids) {
        Set<Product> productSet = new HashSet<>();
        if (allBands != null) {
            for (Band allBand : allBands) {
                productSet.add(allBand.getProduct());
            }
        }
        if (allTiePointGrids != null) {
            for (TiePointGrid allTiePointGrid : allTiePointGrids) {
                productSet.add(allTiePointGrid.getProduct());
            }
        }
        return productSet.size() > 1;
    }

    @Override
    public int show() {
        strategy.updateCheckBoxStates();
        return super.show();
    }

    private void initUI() {
        JPanel checkersPane = strategy.createCheckersPane();

        JCheckBox selectAllCheckBox = new JCheckBox("Select all");
        selectAllCheckBox.setMnemonic('a');
        selectAllCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                strategy.selectAll();
            }
        });

        JCheckBox selectNoneCheckBox = new JCheckBox("Select none");
        selectNoneCheckBox.setMnemonic('n');
        selectNoneCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                strategy.selectNone();
            }
        });

        strategy.setCheckBoxes(selectAllCheckBox, selectNoneCheckBox);

        final JPanel checkPane = new JPanel(new BorderLayout());
        checkPane.add(selectAllCheckBox, BorderLayout.WEST);
        checkPane.add(selectNoneCheckBox, BorderLayout.CENTER);

        TableLayout layout = new TableLayout(1);
        layout.setTablePadding(4, 4);
        JPanel buttonPanel = new JPanel(layout);
        if (addLoadSaveConfigurationButtons) {
            LoadSaveRasterDataNodesConfigurationsProvider provider = new LoadSaveRasterDataNodesConfigurationsProvider(this);
            AbstractButton loadButton = provider.getLoadButton();
            AbstractButton saveButton = provider.getSaveButton();
            buttonPanel.add(loadButton);
            buttonPanel.add(saveButton);
            buttonPanel.add(layout.createVerticalSpacer());
        }

        final JPanel content = new JPanel(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(checkersPane);
        final Dimension preferredSize = checkersPane.getPreferredSize();
        scrollPane.setPreferredSize(new Dimension(Math.min(preferredSize.width + 20, 400),
                Math.min(preferredSize.height + 10, 300)));
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.EAST);
        content.add(checkPane, BorderLayout.SOUTH);
        content.setMinimumSize(new Dimension(0, 100));
        setContent(content);
    }

    @Override
    protected boolean verifyUserInput() {
        if (!strategy.atLeastOneBandSelected() && selectAtLeastOneBand) {
            showInformationDialog("No bands selected.\nPlease select at least one band.");
            return false;
        }
        return true;
    }

    public Band[] getSelectedBands() {
        return strategy.getSelectedBands();
    }

    public TiePointGrid[] getSelectedTiePointGrids() {
        return strategy.getSelectedTiePointGrids();
    }

    @Override
    public void setReadRasterDataNodeNames(String[] readRasterDataNodeNames) {
        strategy.selectNone();
        strategy.selectRasterDataNodes(readRasterDataNodeNames);
    }

    @Override
    public String[] getRasterDataNodeNamesToWrite() {
        Band[] selectedBands = strategy.getSelectedBands();
        TiePointGrid[] selectedTiePointGrids = strategy.getSelectedTiePointGrids();
        String[] nodeNames = new String[selectedBands.length + selectedTiePointGrids.length];
        for (int i = 0; i < selectedBands.length; i++) {
            nodeNames[i] = selectedBands[i].getName();
        }
        for (int i = 0; i < selectedTiePointGrids.length; i++) {
            nodeNames[selectedBands.length + i] = selectedTiePointGrids[i].getName();
        }
        return nodeNames;
    }
}
