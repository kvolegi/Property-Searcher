package com.propertysearcher.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BotGUI extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(BotGUI.class);
    private boolean isBotRunning = false;
    private static volatile BotGUI instance;
    private CreateBot bot;
    private String botName = "";
    private String botToken = "";
    private List<SettingsManager.CategoryEntry> categories = new ArrayList<>();

    private JButton startButton;
    private JButton stopButton;
    private JButton settingButton;
    private JLabel mainTitleLabel;
    private JLabel mainSubtitleLabel;
    private JLabel authorLabel;

    private JPanel contentPane;
    private JPanel topPanel;
    private JPanel titleBlock;
    private JPanel centerPanel;
    private JPanel bottomPanel;

    private static SettingsManager.UiColorTheme currentColorTheme = SettingsManager.getColorPreference();
    private static SettingsManager.UiFontMode currentFontMode = SettingsManager.getFontPreference();
    private static SettingsManager.UiThemeMode currentTheme = SettingsManager.getUiThemePreference();

    public static Font getAppFont(int style, int size) {
        if (currentFontMode == SettingsManager.UiFontMode.SYSTEM) {
            return new Font("Segoe UI", style, size);
        }
        return new Font("Comic Sans MS", style, size);
    }

    public static String getAppFontFamily() {
        return currentFontMode == SettingsManager.UiFontMode.SYSTEM ? "Segoe UI, sans-serif" : "Comic Sans MS, cursive";
    }

    // Palette - dynamic based on active theme
    public static Color COLOR_BG = new Color(182, 234, 233);
    public static Color COLOR_BTN = new Color(133, 221, 218);
    public static Color COLOR_ACCENT = new Color(100, 207, 203);
    public static Color COLOR_FIELD_BG = new Color(210, 245, 244);
    public static Color COLOR_CARD_BG = new Color(248, 253, 253);
    public static Color COLOR_TEXT = new Color(27, 101, 99);

    public static void syncThemeColors() {
        if (currentColorTheme == SettingsManager.UiColorTheme.GRAY) {
            // Elegant modern gray where ALL text is solid black
            COLOR_BG = new Color(204, 208, 214);
            COLOR_BTN = new Color(228, 232, 238);
            COLOR_ACCENT = new Color(185, 190, 198);
            COLOR_FIELD_BG = new Color(245, 246, 248);
            COLOR_CARD_BG = new Color(240, 242, 246);
            COLOR_TEXT = Color.BLACK;
        } else {
            // Restored signature mint style
            COLOR_BG = new Color(182, 234, 233);
            COLOR_BTN = new Color(133, 221, 218);
            COLOR_ACCENT = new Color(100, 207, 203);
            COLOR_FIELD_BG = new Color(210, 245, 244);
            COLOR_CARD_BG = new Color(248, 253, 253);
            COLOR_TEXT = new Color(27, 101, 99);
        }
    }

    public BotGUI() {
        currentColorTheme = SettingsManager.getColorPreference();
        currentFontMode = SettingsManager.getFontPreference();
        currentTheme = (currentColorTheme == SettingsManager.UiColorTheme.GRAY) ? SettingsManager.UiThemeMode.CORPORATE_PRO : SettingsManager.UiThemeMode.COMIC_SANS;
        syncThemeColors();
        checkInitialLanguageAndTheme();
        loadSettings();
        initComponents();
        updateLocalizedTexts();
        instance = this;
    }

    public static BotGUI getInstance() {
        return instance;
    }

    public void setLanguage(I18n.Language newLang) {
        SettingsManager.setLanguage(newLang);
        I18n.setLanguage(newLang);
        updateLocalizedTexts();
        if (bot != null) {
            bot.updateLanguage(newLang);
        }
    }

    public void updateLocalizedTexts() {
        setTitle(I18n.get("app.title"));
        if (startButton != null) startButton.setText(I18n.get("btn.start"));
        if (stopButton != null) stopButton.setText(I18n.get("btn.stop"));
        if (settingButton != null) settingButton.setText(I18n.get("btn.settings"));
    }

    private void checkInitialLanguageAndTheme() {
        if (!SettingsManager.isLanguageConfigured()) {
            showLanguageAndThemeDialog(true, null);
        } else {
            I18n.setLanguage(SettingsManager.getLanguage());
            currentColorTheme = SettingsManager.getColorPreference();
            currentFontMode = SettingsManager.getFontPreference();
            currentTheme = (currentColorTheme == SettingsManager.UiColorTheme.GRAY) ? SettingsManager.UiThemeMode.CORPORATE_PRO : SettingsManager.UiThemeMode.COMIC_SANS;
            syncThemeColors();
        }
    }

    private void showLanguageAndThemeDialog(boolean isFirstLaunch, JDialog parentSettingsDialog) {
        JDialog dlg = new JDialog(this, isFirstLaunch ? "Select Language & UI Style / Налаштування" : I18n.get("theme.dialog_title"), true);
        dlg.getContentPane().setBackground(COLOR_BG);
        dlg.setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(18, 26, 18, 26));

        JLabel titleLbl = new JLabel(I18n.get("theme.prompt"), SwingConstants.CENTER);
        titleLbl.setFont(getAppFont(Font.BOLD, 13));
        titleLbl.setForeground(COLOR_TEXT);
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Language Section
        JLabel langSectionLbl = new JLabel(I18n.get("theme.section_lang"));
        langSectionLbl.setFont(getAppFont(Font.BOLD, 13));
        langSectionLbl.setForeground(COLOR_TEXT);
        langSectionLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton uaRadio = new JRadioButton("Українська", I18n.getLanguage() == I18n.Language.UA);
        JRadioButton enRadio = new JRadioButton("English", I18n.getLanguage() == I18n.Language.EN);
        uaRadio.setFont(getAppFont(Font.PLAIN, 13));
        enRadio.setFont(getAppFont(Font.PLAIN, 13));
        uaRadio.setBackground(COLOR_BG);
        enRadio.setBackground(COLOR_BG);
        uaRadio.setForeground(COLOR_TEXT);
        enRadio.setForeground(COLOR_TEXT);
        uaRadio.setFocusPainted(false);
        enRadio.setFocusPainted(false);

        ButtonGroup langGroup = new ButtonGroup();
        langGroup.add(uaRadio);
        langGroup.add(enRadio);

        JPanel langBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        langBox.setBackground(COLOR_BG);
        langBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        langBox.add(uaRadio);
        langBox.add(enRadio);

        // Font Section
        JLabel fontSectionLbl = new JLabel(I18n.get("theme.section_font"));
        fontSectionLbl.setFont(getAppFont(Font.BOLD, 13));
        fontSectionLbl.setForeground(COLOR_TEXT);
        fontSectionLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton comicRadio = new JRadioButton(I18n.get("theme.font_comic"), currentFontMode == SettingsManager.UiFontMode.COMIC_SANS);
        JRadioButton systemRadio = new JRadioButton(I18n.get("theme.font_system"), currentFontMode == SettingsManager.UiFontMode.SYSTEM);
        comicRadio.setFont(new Font("Comic Sans MS", Font.PLAIN, 13));
        systemRadio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        comicRadio.setBackground(COLOR_BG);
        systemRadio.setBackground(COLOR_BG);
        comicRadio.setForeground(COLOR_TEXT);
        systemRadio.setForeground(COLOR_TEXT);
        comicRadio.setFocusPainted(false);
        systemRadio.setFocusPainted(false);

        ButtonGroup fontGroup = new ButtonGroup();
        fontGroup.add(comicRadio);
        fontGroup.add(systemRadio);

        JPanel fontBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        fontBox.setBackground(COLOR_BG);
        fontBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontBox.add(comicRadio);
        fontBox.add(systemRadio);

        // Color Theme Section
        JLabel colorSectionLbl = new JLabel(I18n.get("theme.section_color"));
        colorSectionLbl.setFont(getAppFont(Font.BOLD, 13));
        colorSectionLbl.setForeground(COLOR_TEXT);
        colorSectionLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton mintRadio = new JRadioButton(I18n.get("theme.color_mint"), currentColorTheme == SettingsManager.UiColorTheme.MINT);
        JRadioButton grayRadio = new JRadioButton(I18n.get("theme.color_gray"), currentColorTheme == SettingsManager.UiColorTheme.GRAY);
        mintRadio.setFont(getAppFont(Font.PLAIN, 13));
        grayRadio.setFont(getAppFont(Font.PLAIN, 13));
        mintRadio.setBackground(COLOR_BG);
        grayRadio.setBackground(COLOR_BG);
        mintRadio.setForeground(COLOR_TEXT);
        grayRadio.setForeground(COLOR_TEXT);
        mintRadio.setFocusPainted(false);
        grayRadio.setFocusPainted(false);

        ButtonGroup colorGroup = new ButtonGroup();
        colorGroup.add(mintRadio);
        colorGroup.add(grayRadio);

        JPanel colorBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        colorBox.setBackground(COLOR_BG);
        colorBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorBox.add(mintRadio);
        colorBox.add(grayRadio);

        // Action button
        JButton applyBtn = createButton(I18n.get("theme.apply_btn"), 0, 0, 260, 42);
        applyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Dynamic text and preview updates
        Runnable updateDialog = () -> {
            boolean isEn = enRadio.isSelected();
            I18n.Language l = isEn ? I18n.Language.EN : I18n.Language.UA;
            dlg.setTitle(l == I18n.Language.UA ? "Мова, шрифт та оформлення" : "Language, Font & Theme");
            titleLbl.setText(l == I18n.Language.UA ?
                    "<html><center><b>Налаштування оформлення</b><br>Оберіть мову, шрифт та колірну тему:</center></html>" :
                    "<html><center><b>Interface Customization</b><br>Choose language, typography and color theme:</center></html>");
            langSectionLbl.setText(l == I18n.Language.UA ? "Мова інтерфейсу:" : "Interface Language:");
            fontSectionLbl.setText(l == I18n.Language.UA ? "Шрифт інтерфейсу:" : "Interface Font:");
            comicRadio.setText(l == I18n.Language.UA ? "Comic Sans MS (Фірмовий)" : "Comic Sans MS (Signature)");
            systemRadio.setText(l == I18n.Language.UA ? "Segoe UI / Системний (Строгий)" : "Segoe UI / System (Standard)");
            colorSectionLbl.setText(l == I18n.Language.UA ? "Колірна тема:" : "Color Theme:");
            mintRadio.setText(l == I18n.Language.UA ? "Фірмова м'ятна" : "Signature Mint");
            grayRadio.setText(l == I18n.Language.UA ? "Елегантна сіра" : "Corporate Slate Gray");
            applyBtn.setText(l == I18n.Language.UA ? "Застосувати" : "Apply");

            boolean isGray = grayRadio.isSelected();
            Color bg = isGray ? new Color(204, 208, 214) : new Color(182, 234, 233);
            Color text = isGray ? Color.BLACK : new Color(27, 101, 99);
            Color btnBg = isGray ? new Color(228, 232, 238) : new Color(133, 221, 218);
            String fontFam = systemRadio.isSelected() ? "Segoe UI" : "Comic Sans MS";

            Font titleF = new Font(fontFam, Font.BOLD, 13);
            Font secF = new Font(fontFam, Font.BOLD, 13);
            Font optF = new Font(fontFam, Font.PLAIN, 13);

            dlg.getContentPane().setBackground(bg);
            mainPanel.setBackground(bg);
            langBox.setBackground(bg);
            fontBox.setBackground(bg);
            colorBox.setBackground(bg);

            titleLbl.setFont(titleF);
            titleLbl.setForeground(text);
            langSectionLbl.setFont(secF);
            langSectionLbl.setForeground(text);
            fontSectionLbl.setFont(secF);
            fontSectionLbl.setForeground(text);
            colorSectionLbl.setFont(secF);
            colorSectionLbl.setForeground(text);

            uaRadio.setFont(optF);
            uaRadio.setBackground(bg);
            uaRadio.setForeground(text);
            enRadio.setFont(optF);
            enRadio.setBackground(bg);
            enRadio.setForeground(text);

            comicRadio.setBackground(bg);
            comicRadio.setForeground(text);
            systemRadio.setBackground(bg);
            systemRadio.setForeground(text);

            mintRadio.setFont(optF);
            mintRadio.setBackground(bg);
            mintRadio.setForeground(text);
            grayRadio.setFont(optF);
            grayRadio.setBackground(bg);
            grayRadio.setForeground(text);

            applyBtn.setFont(new Font(fontFam, Font.PLAIN, 15));
            applyBtn.setBackground(btnBg);
            applyBtn.setForeground(text);

            dlg.repaint();
        };

        uaRadio.addActionListener(e -> updateDialog.run());
        enRadio.addActionListener(e -> updateDialog.run());
        comicRadio.addActionListener(e -> updateDialog.run());
        systemRadio.addActionListener(e -> updateDialog.run());
        mintRadio.addActionListener(e -> updateDialog.run());
        grayRadio.addActionListener(e -> updateDialog.run());

        applyBtn.addActionListener(e -> {
            I18n.Language chosenLang = enRadio.isSelected() ? I18n.Language.EN : I18n.Language.UA;
            SettingsManager.UiFontMode chosenFont = systemRadio.isSelected() ? SettingsManager.UiFontMode.SYSTEM : SettingsManager.UiFontMode.COMIC_SANS;
            SettingsManager.UiColorTheme chosenColor = grayRadio.isSelected() ? SettingsManager.UiColorTheme.GRAY : SettingsManager.UiColorTheme.MINT;

            setLanguage(chosenLang);
            applyTheme(chosenColor, chosenFont);

            dlg.dispose();
            if (parentSettingsDialog != null) {
                parentSettingsDialog.dispose();
                openSettingsDialog();
            }
        });

        mainPanel.add(titleLbl);
        mainPanel.add(Box.createVerticalStrut(14));
        mainPanel.add(langSectionLbl);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(langBox);
        mainPanel.add(Box.createVerticalStrut(14));
        mainPanel.add(fontSectionLbl);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(fontBox);
        mainPanel.add(Box.createVerticalStrut(14));
        mainPanel.add(colorSectionLbl);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(colorBox);
        mainPanel.add(Box.createVerticalStrut(18));
        mainPanel.add(applyBtn);

        dlg.setContentPane(mainPanel);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(480, 360));
        dlg.setLocationRelativeTo(this.isVisible() ? this : null);
        dlg.setVisible(true);
    }

    private Image loadLogoImage() {
        try {
            for (String resName : new String[]{"/logo_512.png", "/logo_256.png", "/logo_128.png", "/logo_64.png", "/logo.png"}) {
                URL res = getClass().getResource(resName);
                if (res != null) {
                    return new ImageIcon(res).getImage();
                }
            }
        } catch (Exception ignored) {}

        for (String path : new String[]{
                "dist/assets/logo_512.png", "dist/assets/logo_256.png", "dist/assets/logo_64.png",
                "assets/logo_512.png", "assets/logo_256.png", "assets/logo_64.png"
        }) {
            File f = new File(path);
            if (f.exists()) {
                return new ImageIcon(f.getAbsolutePath()).getImage();
            }
        }
        return null;
    }

    private void loadSettings() {
        File file = SettingsManager.getSettingsFile();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                botName = reader.readLine();
                botToken = reader.readLine();
            } catch (IOException e) {
                log.warn("Неможливо завантажити налаштування: {}", e.getMessage());
            }
        }
        categories = SettingsManager.loadCategories();
    }

    private void saveAllSettings() {
        SettingsManager.saveSettings(botName, botToken, categories);
    }

    private void initComponents() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(530, 320));
        setResizable(false);
        setTitle(I18n.get("app.title"));

        Image logoImg = loadLogoImage();
        if (logoImg != null) {
            setIconImage(logoImg);
            try {
                if (Taskbar.isTaskbarSupported()) {
                    Taskbar tb = Taskbar.getTaskbar();
                    if (tb.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        tb.setIconImage(logoImg);
                    }
                }
            } catch (Throwable ignored) {}
        }

        contentPane = new JPanel(new BorderLayout(10, 10));
        contentPane.setBackground(COLOR_BG);
        contentPane.setBorder(new EmptyBorder(15, 20, 15, 20));

        // Top Header
        topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        topPanel.setBackground(COLOR_BG);

        if (logoImg != null) {
            Image scaled = logoImg.getScaledInstance(45, 45, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaled));
            topPanel.add(logoLabel);
        }

        titleBlock = new JPanel(new BorderLayout());
        titleBlock.setBackground(COLOR_BG);

        mainTitleLabel = new JLabel("Property Searcher");
        mainTitleLabel.setFont(getAppFont(Font.PLAIN, 26));
        mainTitleLabel.setForeground(COLOR_TEXT);

        mainSubtitleLabel = new JLabel("olx / domria");
        mainSubtitleLabel.setFont(getAppFont(Font.PLAIN, 12));
        mainSubtitleLabel.setForeground(COLOR_TEXT);
        mainSubtitleLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        mainSubtitleLabel.setBorder(new EmptyBorder(0, 0, 0, 4));

        titleBlock.add(mainTitleLabel, BorderLayout.NORTH);
        titleBlock.add(mainSubtitleLabel, BorderLayout.SOUTH);
        topPanel.add(titleBlock);

        // Center Buttons
        centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(COLOR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.insets = new Insets(6, 0, 6, 0);

        startButton = createButton(I18n.get("btn.start"), 0, 0, 240, 56);
        stopButton = createButton(I18n.get("btn.stop"), 0, 0, 240, 56);

        startButton.addActionListener(e -> startBot());
        stopButton.addActionListener(e -> stopBot());

        gbc.gridy = 0;
        centerPanel.add(startButton, gbc);
        gbc.gridy = 1;
        centerPanel.add(stopButton, gbc);

        // Bottom Bar
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(COLOR_BG);

        settingButton = createButton(I18n.get("btn.settings"), 0, 0, 135, 35);
        settingButton.addActionListener(e -> openSettingsDialog());

        authorLabel = new JLabel("@kvolegi");
        authorLabel.setFont(getAppFont(Font.PLAIN, 12));
        authorLabel.setForeground(COLOR_TEXT);

        bottomPanel.add(settingButton, BorderLayout.WEST);
        bottomPanel.add(authorLabel, BorderLayout.EAST);

        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(centerPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        pack();
        setLocationRelativeTo(null);
    }

    public static JButton createButton(String text, int x, int y, int width, int height) {
        JButton button = new JButton(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(COLOR_BTN);
        button.setForeground(COLOR_TEXT);
        button.setFont(getAppFont(Font.PLAIN, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        button.setPreferredSize(new Dimension(width, height));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(COLOR_ACCENT);
                button.setBorder(BorderFactory.createLineBorder(COLOR_TEXT, 2));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(COLOR_BTN);
                button.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                button.setBackground(COLOR_ACCENT);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                button.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            }
        });

        button.setBounds(x, y, width, height);
        return button;
    }

    private void startBot() {
        if (botToken == null || botToken.trim().isEmpty() || botName == null || botName.trim().isEmpty()) {
            ErrorWindow.showErrorWindow(this, I18n.get("gui.token_required_err"));
            return;
        }

        if (categories == null || categories.isEmpty()) {
            ErrorWindow.showErrorWindow(this, I18n.get("gui.categories_required_err"));
            return;
        }

        if (!isBotRunning) {
            bot = new CreateBot(botName, botToken, categories);
            try {
                bot.createBot();
                isBotRunning = true;
            } catch (TelegramApiException e) {
                ErrorWindow.showErrorWindow(this, I18n.get("gui.launch_failed") + e.getMessage());
            }
        } else {
            ErrorWindow.showErrorWindow(this, I18n.get("gui.bot_running_err"));
        }
    }

    private void stopBot() {
        if (isBotRunning && bot != null) {
            isBotRunning = false;
            System.exit(0);
        }
    }

    private void openSettingsDialog() {
        JDialog settingsDialog = new JDialog(this, I18n.get("gui.settings_dialog_title"), true);
        settingsDialog.getContentPane().setBackground(COLOR_BG);
        settingsDialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(COLOR_BG);
        content.setBorder(new EmptyBorder(20, 30, 20, 30));

        JButton tokenButton = createButton(I18n.get("gui.token_btn"), 0, 0, 300, 48);
        JButton categoryButton = createButton(I18n.get("gui.category_btn"), 0, 0, 300, 48);
        JButton instructionButton = createButton(I18n.get("gui.instruction_btn"), 0, 0, 300, 48);
        JButton langButton = createButton(I18n.get("gui.lang_btn"), 0, 0, 300, 48);
        JButton backButton = createButton(I18n.get("gui.back_btn"), 0, 0, 300, 45);

        for (JButton btn : List.of(tokenButton, categoryButton, instructionButton, langButton, backButton)) {
            btn.setMaximumSize(new Dimension(320, 48));
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        tokenButton.addActionListener(e -> openTokenNameDialog(settingsDialog));
        categoryButton.addActionListener(e -> openCategoryDialog(settingsDialog));
        instructionButton.addActionListener(e -> openInstructionDialog(settingsDialog));
        langButton.addActionListener(e -> showLanguageAndThemeDialog(false, settingsDialog));
        backButton.addActionListener(e -> settingsDialog.dispose());

        content.add(tokenButton);
        content.add(Box.createVerticalStrut(12));
        content.add(categoryButton);
        content.add(Box.createVerticalStrut(12));
        content.add(instructionButton);
        content.add(Box.createVerticalStrut(12));
        content.add(langButton);
        content.add(Box.createVerticalStrut(16));
        content.add(backButton);

        settingsDialog.setContentPane(content);
        settingsDialog.pack();
        settingsDialog.setMinimumSize(new Dimension(380, 370));
        settingsDialog.setLocationRelativeTo(this);
        settingsDialog.setVisible(true);
    }

    public void applyTheme(SettingsManager.UiColorTheme color, SettingsManager.UiFontMode font) {
        currentColorTheme = color != null ? color : SettingsManager.UiColorTheme.MINT;
        currentFontMode = font != null ? font : SettingsManager.UiFontMode.COMIC_SANS;
        currentTheme = (currentColorTheme == SettingsManager.UiColorTheme.GRAY) ? SettingsManager.UiThemeMode.CORPORATE_PRO : SettingsManager.UiThemeMode.COMIC_SANS;
        SettingsManager.setColorPreference(currentColorTheme);
        SettingsManager.setFontPreference(currentFontMode);
        syncThemeColors();

        if (contentPane != null) contentPane.setBackground(COLOR_BG);
        if (topPanel != null) topPanel.setBackground(COLOR_BG);
        if (titleBlock != null) titleBlock.setBackground(COLOR_BG);
        if (centerPanel != null) centerPanel.setBackground(COLOR_BG);
        if (bottomPanel != null) bottomPanel.setBackground(COLOR_BG);

        Color labelColor = COLOR_TEXT;
        if (mainTitleLabel != null) {
            mainTitleLabel.setFont(getAppFont(Font.PLAIN, 26));
            mainTitleLabel.setForeground(labelColor);
        }
        if (mainSubtitleLabel != null) {
            mainSubtitleLabel.setFont(getAppFont(Font.PLAIN, 12));
            mainSubtitleLabel.setForeground(labelColor);
        }
        if (authorLabel != null) {
            authorLabel.setFont(getAppFont(Font.PLAIN, 12));
            authorLabel.setForeground(labelColor);
        }
        if (startButton != null) {
            startButton.setFont(getAppFont(Font.PLAIN, 15));
            startButton.setOpaque(true);
            startButton.setContentAreaFilled(true);
            startButton.setBackground(COLOR_BTN);
            startButton.setForeground(COLOR_TEXT);
        }
        if (stopButton != null) {
            stopButton.setFont(getAppFont(Font.PLAIN, 15));
            stopButton.setOpaque(true);
            stopButton.setContentAreaFilled(true);
            stopButton.setBackground(COLOR_BTN);
            stopButton.setForeground(COLOR_TEXT);
        }
        if (settingButton != null) {
            settingButton.setFont(getAppFont(Font.PLAIN, 15));
            settingButton.setOpaque(true);
            settingButton.setContentAreaFilled(true);
            settingButton.setBackground(COLOR_BTN);
            settingButton.setForeground(COLOR_TEXT);
        }

        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    public void applyTheme(SettingsManager.UiThemeMode mode) {
        SettingsManager.UiColorTheme col = (mode == SettingsManager.UiThemeMode.CORPORATE_PRO) ? SettingsManager.UiColorTheme.GRAY : SettingsManager.UiColorTheme.MINT;
        SettingsManager.UiFontMode fnt = (mode == SettingsManager.UiThemeMode.CORPORATE_PRO) ? SettingsManager.UiFontMode.SYSTEM : SettingsManager.UiFontMode.COMIC_SANS;
        applyTheme(col, fnt);
    }

    private void openTokenNameDialog(JDialog parent) {
        JDialog tokenDialog = new JDialog(parent, I18n.get("gui.token_dialog_title"), true);
        tokenDialog.getContentPane().setBackground(COLOR_BG);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(COLOR_BG);
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(COLOR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Font font = getAppFont(Font.PLAIN, 15);

        JLabel nameLabel = new JLabel(I18n.get("gui.bot_name_lbl"));
        nameLabel.setFont(font);
        JTextField nameField = new JTextField(botName, 18);
        nameField.setFont(font);
        nameField.setBackground(COLOR_FIELD_BG);

        JLabel tokenLabel = new JLabel(I18n.get("gui.token_lbl"));
        tokenLabel.setFont(font);
        JTextField tokenField = new JTextField(botToken, 18);
        tokenField.setFont(font);
        tokenField.setBackground(COLOR_FIELD_BG);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        formPanel.add(nameLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        formPanel.add(tokenLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        formPanel.add(tokenField, gbc);

        JButton saveButton = createButton(I18n.get("gui.save_btn"), 0, 0, 160, 42);
        saveButton.setPreferredSize(new Dimension(160, 42));
        saveButton.addActionListener(e -> {
            String token = tokenField.getText().trim();
            if (token.length() < 30) {
                ErrorWindow.showErrorWindow(this, I18n.get("gui.token_len_err"));
            } else {
                botName = nameField.getText().trim();
                botToken = token;
                saveAllSettings();
                tokenDialog.dispose();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setBackground(COLOR_BG);
        btnPanel.add(saveButton);

        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        tokenDialog.setContentPane(mainPanel);
        tokenDialog.pack();
        tokenDialog.setMinimumSize(new Dimension(420, 260));
        tokenDialog.setLocationRelativeTo(parent);
        tokenDialog.setVisible(true);
    }

    private static class CategoryCardRenderer extends JPanel implements ListCellRenderer<SettingsManager.CategoryEntry> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel badgeStatus = new JLabel();
        private final JLabel badgePlatform = new JLabel();
        private final JLabel badgeOwner = new JLabel();
        private final JLabel urlLabel = new JLabel();
        private final JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        public CategoryCardRenderer() {
            setLayout(new BorderLayout(4, 2));
            setOpaque(true);

            Font fontTitle = getAppFont(Font.BOLD, 14);
            Font fontBadge = getAppFont(Font.BOLD, 11);
            Font fontUrl = getAppFont(Font.PLAIN, 11);

            titleLabel.setFont(fontTitle);
            badgeStatus.setFont(fontBadge);
            badgePlatform.setFont(fontBadge);
            badgeOwner.setFont(fontBadge);
            urlLabel.setFont(fontUrl);
            urlLabel.setForeground(new Color(80, 100, 100));

            topRow.setOpaque(false);
            topRow.add(badgeStatus);
            topRow.add(badgePlatform);
            topRow.add(badgeOwner);
            topRow.add(titleLabel);

            add(topRow, BorderLayout.NORTH);
            add(urlLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends SettingsManager.CategoryEntry> list,
                SettingsManager.CategoryEntry value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            titleLabel.setText(CategoryCatalog.localizeCategoryName(value.name, I18n.getLanguage()));

            badgeStatus.setOpaque(true);
            if (value.active) {
                badgeStatus.setText(I18n.get("cat.badge_on"));
                badgeStatus.setBackground(new Color(187, 247, 208));
                badgeStatus.setForeground(new Color(22, 101, 52));
            } else {
                badgeStatus.setText(I18n.get("cat.badge_pause"));
                badgeStatus.setBackground(new Color(229, 231, 235));
                badgeStatus.setForeground(new Color(75, 85, 99));
            }

            badgePlatform.setOpaque(true);
            if (value.url.contains("dom.ria.com")) {
                badgePlatform.setText(" DOM.RIA ");
                badgePlatform.setBackground(new Color(204, 251, 241));
                badgePlatform.setForeground(new Color(15, 118, 110));
            } else {
                badgePlatform.setText(" OLX ");
                badgePlatform.setBackground(new Color(219, 234, 254));
                badgePlatform.setForeground(new Color(29, 78, 216));
            }

            if (value.url.contains("without_agent") || value.url.contains("private")) {
                badgeOwner.setText(I18n.get("cat.badge_owner"));
                badgeOwner.setOpaque(true);
                badgeOwner.setBackground(new Color(254, 240, 138));
                badgeOwner.setForeground(new Color(133, 77, 14));
                badgeOwner.setVisible(true);
            } else {
                badgeOwner.setVisible(false);
            }

            String displayUrl = value.url.length() > 68 ? value.url.substring(0, 68) + "..." : value.url;
            urlLabel.setText(displayUrl);

            if (isSelected) {
                setBackground(new Color(150, 222, 220));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(27, 101, 99), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            } else {
                setBackground(COLOR_BG);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(150, 215, 214)),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            }
            return this;
        }
    }

    private void openCategoryDialog(JDialog parent) {
        categories = SettingsManager.loadCategories();
        JDialog categoryDialog = new JDialog(parent, I18n.get("cat.dialog_title"), true);
        categoryDialog.setSize(620, 530);
        categoryDialog.setLayout(new BorderLayout(10, 10));
        categoryDialog.getContentPane().setBackground(COLOR_BG);
        categoryDialog.setResizable(false);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(COLOR_BG);
        headerPanel.setBorder(new EmptyBorder(12, 16, 6, 16));

        JLabel headerTitle = new JLabel(I18n.get("cat.header_title"));
        headerTitle.setFont(getAppFont(Font.BOLD, 16));
        headerTitle.setForeground(COLOR_TEXT);

        JLabel countBadge = new JLabel(getCountText());
        countBadge.setFont(getAppFont(Font.BOLD, 12));
        countBadge.setForeground(COLOR_TEXT);

        headerPanel.add(headerTitle, BorderLayout.WEST);
        headerPanel.add(countBadge, BorderLayout.EAST);

        DefaultListModel<SettingsManager.CategoryEntry> categoryModel = new DefaultListModel<>();
        for (SettingsManager.CategoryEntry c : categories) {
            categoryModel.addElement(c);
        }

        JList<SettingsManager.CategoryEntry> categoryList = new JList<>(categoryModel);
        categoryList.setCellRenderer(new CategoryCardRenderer());
        categoryList.setFixedCellHeight(60);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setBackground(COLOR_BG);

        JScrollPane scrollPane = new JScrollPane(categoryList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(150, 215, 214), 1));
        scrollPane.getViewport().setBackground(COLOR_BG);

        JPanel topActionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        topActionBar.setBackground(COLOR_BG);

        JButton smartAddBtn = createButton(I18n.get("cat.smart_builder_btn"), 0, 0, 230, 40);
        smartAddBtn.setFont(getAppFont(Font.BOLD, 13));

        JButton manualAddBtn = createButton(I18n.get("cat.manual_add_btn"), 0, 0, 210, 40);
        manualAddBtn.setFont(getAppFont(Font.BOLD, 13));

        topActionBar.add(smartAddBtn);
        topActionBar.add(manualAddBtn);

        JPanel centerContainer = new JPanel(new BorderLayout(5, 5));
        centerContainer.setBackground(COLOR_BG);
        centerContainer.setBorder(new EmptyBorder(0, 16, 0, 16));
        centerContainer.add(topActionBar, BorderLayout.NORTH);
        centerContainer.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 12));
        bottomPanel.setBackground(COLOR_BG);

        JButton toggleActiveBtn = createButton(I18n.get("cat.toggle_btn"), 0, 0, 130, 38);
        JButton editButton = createButton(I18n.get("cat.edit_btn"), 0, 0, 120, 38);
        JButton removeButton = createButton(I18n.get("cat.remove_btn"), 0, 0, 110, 38);
        JButton backButton = createButton(I18n.get("cat.close_btn"), 0, 0, 100, 38);

        bottomPanel.add(toggleActiveBtn);
        bottomPanel.add(editButton);
        bottomPanel.add(removeButton);
        bottomPanel.add(backButton);

        smartAddBtn.addActionListener(e -> {
            new SmartCategoryBuilderDialog(categoryDialog, newCategoryString -> {
                String[] parts = newCategoryString.split("\n");
                String name = parts[0];
                String url = parts.length > 1 ? parts[1] : "";

                SettingsManager.CategoryEntry newEntry = new SettingsManager.CategoryEntry(name, url, true);
                categories.add(newEntry);
                categoryModel.addElement(newEntry);
                saveAllSettings();
                countBadge.setText(getCountText());
            }).setVisible(true);
        });

        manualAddBtn.addActionListener(e -> showManualAddDialog(categoryDialog, categoryModel, countBadge));

        toggleActiveBtn.addActionListener(e -> {
            int idx = categoryList.getSelectedIndex();
            if (idx != -1) {
                SettingsManager.CategoryEntry entry = categoryModel.get(idx);
                entry.active = !entry.active;
                categoryList.repaint();
                saveAllSettings();
                countBadge.setText(getCountText());
            } else {
                JOptionPane.showMessageDialog(categoryDialog, I18n.get("cat.select_active_warning"), I18n.get("dialog.warning_title"), JOptionPane.WARNING_MESSAGE);
            }
        });

        removeButton.addActionListener(e -> {
            int selectedIndex = categoryList.getSelectedIndex();
            if (selectedIndex != -1) {
                categories.remove(selectedIndex);
                categoryModel.remove(selectedIndex);
                saveAllSettings();
                countBadge.setText(getCountText());
            } else {
                JOptionPane.showMessageDialog(categoryDialog, I18n.get("cat.select_delete_warning"), I18n.get("dialog.warning_title"), JOptionPane.WARNING_MESSAGE);
            }
        });

        editButton.addActionListener(e -> {
            int idx = categoryList.getSelectedIndex();
            if (idx != -1) {
                SettingsManager.CategoryEntry entry = categoryModel.get(idx);

                String editedName = JOptionPane.showInputDialog(categoryDialog, I18n.get("cat.edit_name_prompt"), entry.name);
                if (editedName == null || editedName.trim().isEmpty()) return;

                String editedUrl = JOptionPane.showInputDialog(categoryDialog, I18n.get("cat.edit_url_prompt"), entry.url);
                if (editedUrl == null || editedUrl.trim().isEmpty()) return;

                entry.name = editedName.trim();
                entry.url = editedUrl.trim();
                categoryList.repaint();
                saveAllSettings();
            } else {
                JOptionPane.showMessageDialog(categoryDialog, I18n.get("cat.select_edit_warning"), I18n.get("dialog.warning_title"), JOptionPane.WARNING_MESSAGE);
            }
        });

        backButton.addActionListener(e -> categoryDialog.dispose());

        categoryDialog.add(headerPanel, BorderLayout.NORTH);
        categoryDialog.add(centerContainer, BorderLayout.CENTER);
        categoryDialog.add(bottomPanel, BorderLayout.SOUTH);

        categoryDialog.setLocationRelativeTo(parent);
        categoryDialog.setVisible(true);
    }

    private String getCountText() {
        int active = 0;
        for (SettingsManager.CategoryEntry c : categories) {
            if (c.active) active++;
        }
        return I18n.get("cat.total") + ": " + categories.size() + " (" + I18n.get("cat.active") + ": " + active + ")";
    }

    private void showManualAddDialog(
            JDialog parent,
            DefaultListModel<SettingsManager.CategoryEntry> model,
            JLabel countBadge
    ) {
        JDialog dlg = new JDialog(parent, I18n.get("manual.dialog_title"), true);
        dlg.setSize(560, 480);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.getContentPane().setBackground(COLOR_BG);
        dlg.setLocationRelativeTo(parent);

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(COLOR_BG);
        p.setBorder(new EmptyBorder(15, 20, 15, 20));

        Font fontHead = getAppFont(Font.BOLD, 14);
        Font fontInput = getAppFont(Font.PLAIN, 13);

        JLabel titleLbl = new JLabel(I18n.get("manual.name_lbl"));
        titleLbl.setFont(fontHead);
        JTextField titleField = new JTextField();
        titleField.setFont(fontInput);
        titleField.setBackground(COLOR_CARD_BG);
        titleField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel urlLbl = new JLabel(I18n.get("manual.url_lbl"));
        urlLbl.setFont(fontHead);
        JTextField urlField = new JTextField();
        urlField.setFont(new Font("Monospaced", Font.PLAIN, 11));
        urlField.setBackground(COLOR_CARD_BG);
        urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        boolean isEn = (I18n.getLanguage() == I18n.Language.EN);
        String helpHtml = isEn
            ? "<html><head><style>" +
              "body { font-family: 'Comic Sans MS'; font-size: 11px; background-color: #B6EAE9; padding: 5px; margin: 0; }" +
              "b { color: #1B6563; }" +
              "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 1px 4px; font-size: 10px; }" +
              "</style></head><body>" +
              "<b>Search URL Formation Rules:</b><br>" +
              "<b>1. For OLX.ua:</b><br>" +
              "• Set display to <b>rows</b> (not grid): parameter <code>view=list</code>.<br>" +
              "• Set sort to <b>'Newest'</b>: parameter <code>search[order]=created_at:desc</code>.<br>" +
              "• <i>Example:</i> <code>https://www.olx.ua/uk/nedvizhimost/kvartiry/od/?currency=UAH&search[order]=created_at:desc&view=list</code><br><br>" +
              "<b>2. For DOM.RIA.com:</b><br>" +
              "• Choose city, deal type, and enable 'Without agents'.<br>" +
              "• <i>Example:</i> <code>https://dom.ria.com/uk/prodazha-kvartir/odessa/?without_agent=1</code>" +
              "</body></html>"
            : "<html><head><style>" +
              "body { font-family: 'Comic Sans MS'; font-size: 11px; background-color: #B6EAE9; padding: 5px; margin: 0; }" +
              "b { color: #1B6563; }" +
              "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 1px 4px; font-size: 10px; }" +
              "</style></head><body>" +
              "<b>Правила формування посилання:</b><br>" +
              "<b>1. Для OLX.ua:</b><br>" +
              "• Оберіть показ <b>рядками</b> (не плиткою): параметр <code>view=list</code>.<br>" +
              "• Оберіть сортування <b>«Найновіші»</b>: параметр <code>search[order]=created_at:desc</code>.<br>" +
              "• <i>Приклад:</i> <code>https://www.olx.ua/uk/nedvizhimost/kvartiry/od/?currency=UAH&search[order]=created_at:desc&view=list</code><br><br>" +
              "<b>2. Для DOM.RIA.com:</b><br>" +
              "• Оберіть місто, купівлю/оренду та увімкніть «Без посередників».<br>" +
              "• <i>Приклад:</i> <code>https://dom.ria.com/uk/prodazha-kvartir/odessa/?without_agent=1</code>" +
              "</body></html>";

        JEditorPane helpPane = new JEditorPane("text/html", helpHtml);
        helpPane.setEditable(false);
        helpPane.setBackground(COLOR_BG);

        p.add(titleLbl);
        p.add(Box.createRigidArea(new Dimension(0, 5)));
        p.add(titleField);
        p.add(Box.createRigidArea(new Dimension(0, 12)));
        p.add(urlLbl);
        p.add(Box.createRigidArea(new Dimension(0, 5)));
        p.add(urlField);
        p.add(Box.createRigidArea(new Dimension(0, 12)));
        p.add(helpPane);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(COLOR_BG);

        JButton addBtn = createButton(I18n.get("manual.add_btn"), 0, 0, 130, 38);
        JButton cancelBtn = createButton(I18n.get("manual.cancel_btn"), 0, 0, 110, 38);

        addBtn.addActionListener(ev -> {
            String t = titleField.getText().trim();
            String u = urlField.getText().trim();
            if (t.isEmpty() || u.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, I18n.get("manual.enter_warning"), I18n.get("dialog.warning_title"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            SettingsManager.CategoryEntry newEntry = new SettingsManager.CategoryEntry(t, u, true);
            categories.add(newEntry);
            model.addElement(newEntry);
            saveAllSettings();
            countBadge.setText(getCountText());
            dlg.dispose();
        });

        cancelBtn.addActionListener(ev -> dlg.dispose());

        btnPanel.add(addBtn);
        btnPanel.add(cancelBtn);

        dlg.add(p, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void openInstructionDialog(JDialog parent) {
        JDialog instructionDialog = new JDialog(parent, I18n.get("ins.dialog_title"), true);
        instructionDialog.setLayout(new BorderLayout(10, 10));
        instructionDialog.getContentPane().setBackground(COLOR_BG);
        instructionDialog.setResizable(true);

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        headerPanel.setBackground(COLOR_BG);

        Image logoImg = loadLogoImage();
        if (logoImg != null) {
            Image scaled = logoImg.getScaledInstance(44, 44, Image.SCALE_SMOOTH);
            headerPanel.add(new JLabel(new ImageIcon(scaled)));
        }

        JLabel titleLabel = new JLabel("Property Searcher — " + I18n.get("ins.dialog_title"));
        titleLabel.setFont(getAppFont(Font.BOLD, 18));
        titleLabel.setForeground(COLOR_TEXT);
        headerPanel.add(titleLabel);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(getAppFont(Font.BOLD, 13));
        tabbedPane.setBackground(COLOR_BG);

        JPanel botFatherTab = new JPanel(new BorderLayout(10, 10));
        botFatherTab.setBackground(COLOR_BG);
        botFatherTab.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel guideBox = new JPanel();
        guideBox.setLayout(new BoxLayout(guideBox, BoxLayout.Y_AXIS));
        guideBox.setBackground(COLOR_BG);

        JLabel stepTitle = new JLabel(I18n.get("ins.botfather_step_title"));
        stepTitle.setFont(getAppFont(Font.BOLD, 14));
        stepTitle.setForeground(COLOR_TEXT);

        JLabel stepDesc = new JLabel("<html><body style='width: 580px; font-family: " + getAppFontFamily() + "; font-size: 11px;'>" +
                I18n.get("ins.botfather_step_desc") + "</body></html>");

        guideBox.add(stepTitle);
        guideBox.add(Box.createRigidArea(new Dimension(0, 6)));
        guideBox.add(stepDesc);

        JTextArea codeArea = new JTextArea(I18n.get("ins.botfather_commands"));
        codeArea.setFont(getAppFont(Font.BOLD, 14));
        codeArea.setBackground(COLOR_FIELD_BG);
        codeArea.setForeground(COLOR_TEXT);
        codeArea.setEditable(false);
        codeArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BTN, 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        JScrollPane codeScroll = new JScrollPane(codeArea);
        codeScroll.setBorder(null);

        JButton copyCommandsButton = createButton(I18n.get("ins.copy_btn"), 0, 0, 380, 42);
        copyCommandsButton.setFont(getAppFont(Font.BOLD, 14));
        copyCommandsButton.addActionListener(e -> {
            String botFatherCommands = I18n.get("ins.botfather_commands");

            StringSelection selection = new StringSelection(botFatherCommands);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);

            copyCommandsButton.setText(I18n.get("ins.copied_button_text"));
            Timer t = new Timer(2500, evt -> copyCommandsButton.setText(I18n.get("ins.copy_btn")));
            t.setRepeats(false);
            t.start();

            JOptionPane.showMessageDialog(instructionDialog,
                    I18n.get("ins.copied_notice"),
                    I18n.get("ins.copied_title"), JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel copyBtnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        copyBtnWrap.setBackground(COLOR_BG);
        copyBtnWrap.add(copyCommandsButton);

        botFatherTab.add(guideBox, BorderLayout.NORTH);
        botFatherTab.add(codeScroll, BorderLayout.CENTER);
        botFatherTab.add(copyBtnWrap, BorderLayout.SOUTH);

        I18n.Language currentLang = I18n.getLanguage();
        JEditorPane quickStartPane = new JEditorPane("text/html", getQuickStartHtml(currentLang));
        quickStartPane.setEditable(false);
        quickStartPane.setBackground(COLOR_BG);
        quickStartPane.setCaret(null);
        JScrollPane tab2Scroll = new JScrollPane(quickStartPane);

        JEditorPane filtersPane = new JEditorPane("text/html", getFiltersAndArchitectureHtml(currentLang));
        filtersPane.setEditable(false);
        filtersPane.setBackground(COLOR_BG);
        filtersPane.setCaret(null);
        JScrollPane tab3Scroll = new JScrollPane(filtersPane);

        tabbedPane.addTab(I18n.get("ins.tab_botfather"), botFatherTab);
        tabbedPane.addTab(I18n.get("ins.tab_quickstart"), tab2Scroll);
        tabbedPane.addTab(I18n.get("ins.tab_filters"), tab3Scroll);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        bottomPanel.setBackground(COLOR_BG);

        JButton okButton = createButton(I18n.get("ins.close_btn"), 0, 0, 110, 38);
        okButton.addActionListener(e -> instructionDialog.dispose());
        bottomPanel.add(okButton);

        instructionDialog.add(headerPanel, BorderLayout.NORTH);
        instructionDialog.add(tabbedPane, BorderLayout.CENTER);
        instructionDialog.add(bottomPanel, BorderLayout.SOUTH);

        instructionDialog.setSize(780, 600);
        instructionDialog.setLocationRelativeTo(parent);
        instructionDialog.setVisible(true);
    }

    private String getQuickStartHtml(I18n.Language lang) {
        if (lang == I18n.Language.EN) {
            return "<html><head><style>" +
                    "body { font-family: 'Comic Sans MS'; font-size: 12px; background-color: #B6EAE9; padding: 15px; }" +
                    "h2 { font-size: 18px; font-weight: bold; color: #1B6563; }" +
                    "h3 { font-size: 14px; font-weight: bold; color: #1B6563; margin-top: 14px; }" +
                    "p, li { font-size: 12px; line-height: 1.4; }" +
                    "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 2px 5px; font-weight: bold; }" +
                    "</style></head><body>" +
                    "<h2>Instant Monitoring Quick Start</h2>" +
                    "<p>This platform is designed to intercept freshly published listings from <b>OLX.ua</b> and <b>DOM.RIA</b> directly into Telegram!</p>" +
                    "<h3>Instant Initial Dispatch:</h3>" +
                    "<p>Whenever you start monitoring or press <i>'Search Now'</i> in Telegram, the bot <b>immediately sends the latest organic listing</b> for each category with full photo album, price, and exact timestamp!</p>" +
                    "<h3>Telegram Management Controls:</h3>" +
                    "<ul>" +
                    "<li><b>'Search Now' button</b> — runs immediate poll of newest listings.</li>" +
                    "<li><b>'My Categories' button</b> — displays all targets with interactive [Pause / Resume] and [Delete] buttons.</li>" +
                    "<li><b>'System Status' button</b> — shows thread health, adaptive delay, and current req/s rate.</li>" +
                    "<li><b>'Stop' button</b> — pauses monitoring daemon.</li>" +
                    "<li><b>'Open on ...' button</b> — opens listing directly in browser.</li>" +
                    "</ul>" +
                    "</body></html>";
        }

        return "<html><head><style>" +
                "body { font-family: 'Comic Sans MS'; font-size: 12px; background-color: #B6EAE9; padding: 15px; }" +
                "h2 { font-size: 18px; font-weight: bold; color: #1B6563; }" +
                "h3 { font-size: 14px; font-weight: bold; color: #1B6563; margin-top: 14px; }" +
                "p, li { font-size: 12px; line-height: 1.4; }" +
                "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 2px 5px; font-weight: bold; }" +
                "</style></head><body>" +
                "<h2>Миттєвий старт моніторингу</h2>" +
                "<p>Програма призначена для блискавичного перехоплення найсвіжіших оголошень з <b>OLX.ua</b> та <b>DOM.RIA</b> прямо в Telegram!</p>" +
                "<h3>Миттєва відправка при старті:</h3>" +
                "<p>Як тільки ви запускаєте будь-яку категорію (або тиснете кнопку <i>«Пошук зараз»</i> у Telegram), бот <b>одразу знаходить і надсилає найновіше органічне оголошення</b> в цій категорії разом із його фотографією, ціною та точним часом публікації (години:хвилини)!</p>" +
                "<h3>Зручне керування в Telegram:</h3>" +
                "<ul>" +
                "<li><b>Кнопка «Пошук зараз»</b> — запускає швидку перевірку найсвіжіших оголошень.</li>" +
                "<li><b>Кнопка «Мої категорії»</b> — показує повний перелік активних цілей моніторингу та їхній статус (ВКЛ / ПАУЗА).</li>" +
                "<li><b>Кнопка «Статус системи»</b> — показує активність потоків і платформ.</li>" +
                "<li><b>Кнопка «Зупинити»</b> — призупиняє фоновий моніторинг.</li>" +
                "<li><b>Кнопка «Відкрити на ...»</b> під кожним оголошенням — миттєво відкриває об'єкт у браузері або додатку.</li>" +
                "</ul>" +
                "</body></html>";
    }

    private String getFiltersAndArchitectureHtml(I18n.Language lang) {
        if (lang == I18n.Language.EN) {
            return "<html><head><style>" +
                    "body { font-family: 'Comic Sans MS'; font-size: 12px; background-color: #B6EAE9; padding: 15px; }" +
                    "h2 { font-size: 18px; font-weight: bold; color: #1B6563; }" +
                    "h3 { font-size: 14px; font-weight: bold; color: #1B6563; margin-top: 14px; }" +
                    "p, li { font-size: 12px; line-height: 1.4; }" +
                    "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 2px 5px; font-weight: bold; }" +
                    "</style></head><body>" +
                    "<h2>Filter Architecture & Reliability</h2>" +
                    "<h3>1. Direct Owner Filter (100% No Agencies):</h3>" +
                    "<p>When enabled, queries automatically attach <code>without_agent=1</code> on DOM.RIA and <code>search[private_business]=private</code> on OLX.</p>" +
                    "<h3>2. Complete Sponsored Ad Exclusion:</h3>" +
                    "<p>Paid promoted ads (TOP / Featured) pinned at the top of pages are strictly ignored. The bot intercepts <b>only authentic new listings</b>.</p>" +
                    "<h3>3. O(1) 50-Slot Circular Memory Ring Buffer:</h3>" +
                    "<p>Eliminates duplicate alerts if aggregator portals reorder listings. Cold start silently caches active baselines to eliminate startup spam.</p>" +
                    "<h3>4. CDN Propagation Physics (1.8s Lower Bound):</h3>" +
                    "<p>Aggregators take 30–90 seconds for moderation and CDN propagation. Property Searcher intercepts listings within <b>1–2 seconds</b> of public edge availability!</p>" +
                    "<h3>5. Automatic NBU DUAL Currency & Night Silent Mode:</h3>" +
                    "<p>Live conversions (e.g. <code>$65 000 (≈ 2 704 000 UAH)</code>). Silent notifications between 23:00 and 07:00.</p>" +
                    "</body></html>";
        }

        return "<html><head><style>" +
                "body { font-family: 'Comic Sans MS'; font-size: 12px; background-color: #B6EAE9; padding: 15px; }" +
                "h2 { font-size: 18px; font-weight: bold; color: #1B6563; }" +
                "h3 { font-size: 14px; font-weight: bold; color: #1B6563; margin-top: 14px; }" +
                "p, li { font-size: 12px; line-height: 1.4; }" +
                "code { background-color: #D2F5F4; border: 1px solid #1B6563; padding: 2px 5px; font-weight: bold; }" +
                "</style></head><body>" +
                "<h2>Архітектура фільтрів та надійність</h2>" +
                "<h3>1. Фільтр «Тільки від власника» (100% без посередників):</h3>" +
                "<p>При увімкненні параметра «Без посередників» у Конструкторі категорій до запиту автоматично додається системний фільтр: <code>without_agent=1</code> для DOM.RIA та <code>search[private_business]=private</code> для OLX. Ріелтори та агенції автоматично відсікаються.</p>" +
                "<h3>2. Повне ігнорування рекламних ТОП-оголошень:</h3>" +
                "<p>Платні просувані оголошення (TOP / Featured), які постійно закріплені вгорі сторінки, автоматично пропускаються. Бот перехоплює <b>тільки реальні нові надходження</b>.</p>" +
                "<h3>3. Захист від перестановок (Circular Buffer пам'яті на 50 слотів):</h3>" +
                "<p>Внутрішній кільцевий буфер пам'яті перевіряє останні 50 надісланих посилань. На старті бот тихо кешує 30 активних оголошень, тому спам старими записами повністю виключений.</p>" +
                "<h3>4. Фізика швидкості та CDN-кешування сайтів (OLX & DOM.RIA):</h3>" +
                "<p>Після публікації оголошення потрапляє на модерацію сайту і стає доступним на публічних серверах через <b>30–90 секунд</b>. Property Searcher виявляє його <b>в перші ж 1–2 секунди</b> після появи у відкритому веб-просторі!</p>" +
                "<h3>5. Автоматичний DUAL-курс НБУ та Нічний тихий режим:</h3>" +
                "<p>Усі ціни автоматично транслюються у подвійному форматі за курсом НБУ. У нічний час (23:00 – 07:00) повідомлення приходять без звуку.</p>" +
                "</body></html>";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BotGUI().setVisible(true));
    }
}
