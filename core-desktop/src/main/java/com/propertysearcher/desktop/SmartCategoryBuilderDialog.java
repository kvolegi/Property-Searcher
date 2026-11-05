package com.propertysearcher.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.function.Consumer;

public class SmartCategoryBuilderDialog extends JDialog {

    private final JComboBox<String> platformCombo;
    private final JComboBox<CategoryCatalog.City> cityCombo;
    private final JComboBox<CategoryCatalog.PropertyType> propertyTypeCombo;
    private final JComboBox<CategoryCatalog.DealType> dealTypeCombo;
    private final JTextField minPriceField;
    private final JTextField maxPriceField;
    private final JCheckBox ownerCheckBox;
    private final JTextField customTitleField;
    private final JTextField previewUrlField;

    public SmartCategoryBuilderDialog(Dialog owner, Consumer<String> onCategoryCreated) {
        super(owner, I18n.get("builder.dialog_title"), true);
        setSize(560, 520);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(BotGUI.COLOR_BG);
        setLocationRelativeTo(owner);
        setResizable(false);

        Font fontLabel = BotGUI.getAppFont(Font.BOLD, 13);
        Font fontInput = BotGUI.getAppFont(Font.PLAIN, 13);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(8, 2, 10, 10));
        mainPanel.setBackground(BotGUI.COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        // 1. Platform
        mainPanel.add(createLabel(I18n.get("builder.platform_lbl"), fontLabel));
        platformCombo = new JComboBox<>(new String[]{"DOM.RIA", "OLX"});
        platformCombo.setFont(fontInput);
        platformCombo.setBackground(BotGUI.COLOR_FIELD_BG);
        platformCombo.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(platformCombo);

        // 2. City
        mainPanel.add(createLabel(I18n.get("builder.city_lbl"), fontLabel));
        cityCombo = new JComboBox<>(CategoryCatalog.getCities().toArray(new CategoryCatalog.City[0]));
        cityCombo.setFont(fontInput);
        cityCombo.setBackground(BotGUI.COLOR_FIELD_BG);
        cityCombo.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(cityCombo);

        // 3. Property Type
        mainPanel.add(createLabel(I18n.get("builder.type_lbl"), fontLabel));
        propertyTypeCombo = new JComboBox<>(CategoryCatalog.getPropertyTypes().toArray(new CategoryCatalog.PropertyType[0]));
        propertyTypeCombo.setFont(fontInput);
        propertyTypeCombo.setBackground(BotGUI.COLOR_FIELD_BG);
        propertyTypeCombo.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(propertyTypeCombo);

        // 4. Deal Type
        mainPanel.add(createLabel(I18n.get("builder.deal_lbl"), fontLabel));
        dealTypeCombo = new JComboBox<>(CategoryCatalog.getDealTypes().toArray(new CategoryCatalog.DealType[0]));
        dealTypeCombo.setFont(fontInput);
        dealTypeCombo.setBackground(BotGUI.COLOR_FIELD_BG);
        dealTypeCombo.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(dealTypeCombo);

        // 5. Price Filter
        mainPanel.add(createLabel(I18n.get("builder.price_min_lbl"), fontLabel));
        JPanel pricePanel = new JPanel(new GridLayout(1, 2, 5, 0));
        pricePanel.setBackground(BotGUI.COLOR_BG);

        TitledBorder fromBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BotGUI.COLOR_ACCENT, 1),
                I18n.get("builder.price_from"),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                BotGUI.getAppFont(Font.PLAIN, 11),
                BotGUI.COLOR_TEXT
        );
        TitledBorder toBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BotGUI.COLOR_ACCENT, 1),
                I18n.get("builder.price_to"),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                BotGUI.getAppFont(Font.PLAIN, 11),
                BotGUI.COLOR_TEXT
        );

        minPriceField = new JTextField();
        minPriceField.setFont(fontInput);
        minPriceField.setBackground(BotGUI.COLOR_FIELD_BG);
        minPriceField.setForeground(BotGUI.COLOR_TEXT);
        minPriceField.setBorder(fromBorder);

        maxPriceField = new JTextField();
        maxPriceField.setFont(fontInput);
        maxPriceField.setBackground(BotGUI.COLOR_FIELD_BG);
        maxPriceField.setForeground(BotGUI.COLOR_TEXT);
        maxPriceField.setBorder(toBorder);

        pricePanel.add(minPriceField);
        pricePanel.add(maxPriceField);
        mainPanel.add(pricePanel);

        // 6. Owner Only Checkbox
        mainPanel.add(createLabel(I18n.get("builder.owner_chk"), fontLabel));
        ownerCheckBox = new JCheckBox(I18n.get("builder.direct_owner"));
        ownerCheckBox.setFont(fontInput);
        ownerCheckBox.setBackground(BotGUI.COLOR_BG);
        ownerCheckBox.setForeground(BotGUI.COLOR_TEXT);
        ownerCheckBox.setFocusPainted(false);
        mainPanel.add(ownerCheckBox);

        // 7. Custom Title
        mainPanel.add(createLabel(I18n.get("builder.custom_title_lbl"), fontLabel));
        customTitleField = new JTextField();
        customTitleField.setFont(fontInput);
        customTitleField.setBackground(BotGUI.COLOR_FIELD_BG);
        customTitleField.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(customTitleField);

        // 8. Auto-generated URL Preview
        mainPanel.add(createLabel(I18n.get("builder.preview_url_lbl"), fontLabel));
        previewUrlField = new JTextField();
        previewUrlField.setFont(new Font("Monospaced", Font.PLAIN, 11));
        previewUrlField.setEditable(false);
        previewUrlField.setBackground(BotGUI.COLOR_FIELD_BG);
        previewUrlField.setForeground(BotGUI.COLOR_TEXT);
        mainPanel.add(previewUrlField);

        add(mainPanel, BorderLayout.CENTER);

        // Auto update preview URL & Title on changes
        Runnable updateAction = this::refreshPreview;
        platformCombo.addActionListener(e -> updateAction.run());
        cityCombo.addActionListener(e -> updateAction.run());
        propertyTypeCombo.addActionListener(e -> updateAction.run());
        dealTypeCombo.addActionListener(e -> updateAction.run());
        ownerCheckBox.addActionListener(e -> updateAction.run());

        minPriceField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) { updateAction.run(); }
        });
        maxPriceField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent e) { updateAction.run(); }
        });

        // Bottom Button Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomBar.setBackground(BotGUI.COLOR_BG);

        JButton createBtn = BotGUI.createButton(I18n.get("builder.add_btn"), 0, 0, 200, 42);
        createBtn.setFont(BotGUI.getAppFont(Font.BOLD, 15));

        JButton cancelBtn = BotGUI.createButton(I18n.get("builder.cancel_btn"), 0, 0, 120, 42);
        cancelBtn.setFont(BotGUI.getAppFont(Font.PLAIN, 14));

        createBtn.addActionListener(e -> {
            String title = customTitleField.getText().trim();
            String url = previewUrlField.getText().trim();
            if (title.isEmpty() || url.isEmpty()) {
                JOptionPane.showMessageDialog(this, I18n.get("builder.enter_warning"), I18n.get("dialog.warning_title"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            onCategoryCreated.accept(title + "\n" + url);
            dispose();
        });

        cancelBtn.addActionListener(e -> dispose());

        bottomBar.add(createBtn);
        bottomBar.add(cancelBtn);
        add(bottomBar, BorderLayout.SOUTH);

        refreshPreview();
    }

    private JLabel createLabel(String text, Font font) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(BotGUI.COLOR_TEXT);
        return l;
    }

    private void refreshPreview() {
        String platform = (String) platformCombo.getSelectedItem();
        CategoryCatalog.City city = (CategoryCatalog.City) cityCombo.getSelectedItem();
        CategoryCatalog.PropertyType pt = (CategoryCatalog.PropertyType) propertyTypeCombo.getSelectedItem();
        CategoryCatalog.DealType dt = (CategoryCatalog.DealType) dealTypeCombo.getSelectedItem();

        if (city == null || pt == null || dt == null) return;

        Integer minPrice = null;
        Integer maxPrice = null;
        try {
            if (!minPriceField.getText().trim().isEmpty()) {
                minPrice = Integer.parseInt(minPriceField.getText().trim().replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}
        try {
            if (!maxPriceField.getText().trim().isEmpty()) {
                maxPrice = Integer.parseInt(maxPriceField.getText().trim().replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) {}

        boolean owner = ownerCheckBox.isSelected();

        String url = CategoryCatalog.buildUrl(platform, city, pt, dt, minPrice, maxPrice, owner);
        previewUrlField.setText(url);

        // Auto Title with active language
        I18n.Language currentLang = I18n.getLanguage();
        String typeName = pt.getName(currentLang);
        String cityName = city.getName(currentLang);
        String dealName = dt.getName(currentLang);
        String ownerBadge = owner ? I18n.get("builder.owner_suffix") : "";

        String autoTitle = typeName + " " + cityName + " (" + dealName + ")" + ownerBadge;

        String currentText = customTitleField.getText().trim();
        if (currentText.isEmpty() || currentText.contains("Квартири") || currentText.contains("Apartments") || currentText.contains("Будинки") || currentText.contains("Houses")) {
            customTitleField.setText(autoTitle);
        }
    }
}
