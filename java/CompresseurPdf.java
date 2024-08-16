package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CompresseurPdf extends JFrame {
    private static final long serialVersionUID = 1L;
    private JTextField filePathField;
    private JRadioButton[] compressionOptions;
    private File selectedFile;
    private JLabel statusLabel;
    private JFileChooser fileChooser;
    private File lastDirectory;

    public CompresseurPdf() {
        setTitle("Compresseur PDF");
        setSize(600, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        JButton fileButton = new JButton("Fichier");
        filePathField = new JTextField(40);
        filePathField.setEditable(false);
        filePanel.add(fileButton, BorderLayout.WEST);
        filePanel.add(filePathField, BorderLayout.CENTER);
        panel.add(filePanel, BorderLayout.NORTH);

        JPanel compressionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel compressionLabel = new JLabel("Niveau de compression : ");
        compressionPanel.add(compressionLabel);

        ButtonGroup compressionGroup = new ButtonGroup();
        compressionOptions = new JRadioButton[3];
        String[] options = {"Forte", "Moyenne", "Faible"};
        for (int i = 0; i < 3; i++) {
            compressionOptions[i] = new JRadioButton(options[i]);
            compressionGroup.add(compressionOptions[i]);
            compressionPanel.add(compressionOptions[i]);
        }
        compressionOptions[0].setSelected(true);
        panel.add(compressionPanel, BorderLayout.CENTER);

        JPanel compressPanel = new JPanel(new GridLayout(2, 1));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton compressButton = new JButton("Compresser");
        buttonPanel.add(compressButton);
        statusLabel = new JLabel("");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        compressPanel.add(buttonPanel);
        compressPanel.add(statusLabel);
        panel.add(compressPanel, BorderLayout.SOUTH);

        add(panel);

        // Initialisation du JFileChooser
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        fileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Rétablir le dernier répertoire sélectionné
                if (lastDirectory != null) {
                    fileChooser.setCurrentDirectory(lastDirectory);
                }

                // Afficher la vue "Détails"
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        Action detailsAction = fileChooser.getActionMap().get("viewTypeDetails");
                        if (detailsAction != null) {
                            detailsAction.actionPerformed(null);
                        }
                    }
                });

                int result = fileChooser.showOpenDialog(CompresseurPdf.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    selectedFile = fileChooser.getSelectedFile();
                    filePathField.setText(selectedFile.getAbsolutePath());
                    lastDirectory = selectedFile.getParentFile();
                }
            }
        });

        compressButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedFile != null) {
                    statusLabel.setText("Traitement en cours...");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            int compressionLevel = getSelectedCompressionLevel();
                            try {
                                compressPdf(selectedFile.getAbsolutePath(), compressionLevel);
                                statusLabel.setText("Traitement terminé");
                            } catch (IOException | InterruptedException ex) {
                                ex.printStackTrace();
                                JOptionPane.showMessageDialog(CompresseurPdf.this, "Echec de la compression : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                                statusLabel.setText("");
                            }
                        }
                    }).start();
                } else {
                    JOptionPane.showMessageDialog(CompresseurPdf.this, "Veuillez sélectionner un fichier à compresser.", "Attention", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }

    private int getSelectedCompressionLevel() {
        for (int i = 0; i < compressionOptions.length; i++) {
            if (compressionOptions[i].isSelected()) {
                return i + 1;
            }
        }
        return 1;
    }

    public static void main(String[] args) {
        if (!isJavaInstalled()) {
            int option = JOptionPane.showOptionDialog(null,
                    "Java n'est pas installé sur le poste. Cliquez sur le bouton 'Installer' pour installer Java ou cliquez sur 'Annuler' pour quitter l'application.",
                    "Java non installé",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[]{"Installer", "Annuler"},
                    "Installer");

            if (option == JOptionPane.YES_OPTION) {
                openJavaDownloadPage();
            } else {
                System.exit(0);
            }
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    new CompresseurPdf().setVisible(true);
                }
            });
        }
    }

    private static boolean isJavaInstalled() {
        try {
            Process process = Runtime.getRuntime().exec("java -version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line = reader.readLine();
            if (line != null && line.contains("version")) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static void openJavaDownloadPage() {
        try {
            Desktop.getDesktop().browse(new java.net.URI("https://www.java.com/en/download/"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void compressPdf(String fichierSource, int niveauCompression) throws IOException, InterruptedException {
        // Verify if the source file exists and is not a directory
        File sourceFile = new File(fichierSource);
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            System.err.println("Le fichier source n'existe pas ou est un répertoire");
            return;
        }

        // Verify if the file is read-only and make it writable if necessary
        if (!sourceFile.canWrite()) {
            if (!sourceFile.setWritable(true)) {
                System.err.println("Impossible de rendre le fichier modifiable.");
                return;
            }
        }

        // Use a temporary file for the compressed file
        Path fichierCompresseTemp = Files.createTempFile("compressed_", ".pdf");

        // Extract Ghostscript and its DLLs from the resources
        Path tempDir = Files.createTempDirectory("ghostscript");
        String[] ghostscriptFiles = {"gswin64c.exe", "gsdll64.dll"};

        for (String fileName : ghostscriptFiles) {
            try (InputStream in = CompresseurPdf.class.getResourceAsStream("/ghostscript/" + fileName)) {
                if (in == null) {
                    System.err.println("Le fichier " + fileName + " est introuvable dans les ressources.");
                    return;
                }
                Files.copy(in, tempDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Make the files executable (necessary on Linux/Unix)
        File ghostscriptExecutable = tempDir.resolve("gswin64c.exe").toFile();
        ghostscriptExecutable.setExecutable(true);

        // Define the PDF compression level based on the provided argument or the default value
        String pdfSettings;
        switch (niveauCompression) {
            case 1:
                pdfSettings = "/screen";
                break;
            case 2:
                pdfSettings = "/ebook";
                break;
            case 3:
                pdfSettings = "/printer";
                break;
            case 4:
                pdfSettings = "/prepress";
                break;
            default:
                pdfSettings = "/screen";  // Set '/screen' as the default level for any unrecognized or absent entry
                break;
        }

        // Execute the Ghostscript command with the selected compression level
        String[] commande = {
            ghostscriptExecutable.getAbsolutePath(),
            "-sDEVICE=pdfwrite",
            "-dCompatibilityLevel=1.5",
            "-dPDFSETTINGS=" + pdfSettings,
            "-dNOPAUSE",
            "-dQUIET",
            "-dBATCH",
            "-dEmbedAllFonts=false",  // Do not embed all fonts
            "-dSubsetFonts=true",  // Embed only subsets of used fonts
            //"-dColorImageDownsampleType=/Bicubic",  // Downsample type for color images
            //"-dColorImageResolution=72",  // Lower the resolution of color images
            //"-dGrayImageDownsampleType=/Bicubic",  // Downsample type for grayscale images
            //"-dGrayImageResolution=72",  // Lower the resolution of grayscale images
            //"-dMonoImageDownsampleType=/Subsample",  // Downsample type for monochrome images
            //"-dMonoImageResolution=72",  // Lower the resolution of monochrome images
            "-sOutputFile=" + fichierCompresseTemp.toString(),
            sourceFile.getAbsolutePath()
        };

        Process process = new ProcessBuilder(commande)
            .directory(tempDir.toFile())
            .start();

        // Read the standard output and error of the process
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
        }

        // Wait for the process to complete
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Fichier PDF compressé avec succès");
            Files.move(fichierCompresseTemp, sourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.err.println("La compression du fichier PDF a échoué.");
        }

        // Delete the temporary files
        for (String fileName : ghostscriptFiles) {
            new File(tempDir.resolve(fileName).toString()).delete();
        }
        tempDir.toFile().delete();
        fichierCompresseTemp.toFile().delete();
    }
}
