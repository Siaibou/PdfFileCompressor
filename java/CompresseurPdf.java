package com.example;

import java.io.*; // Importation des classes nécessaires pour les opérations d'entrée/sortie
import java.nio.file.Files; // Importation de la classe Files pour la manipulation des fichiers et des répertoires
import java.nio.file.Path; // Importation de la classe Path pour représenter les chemins dans le système de fichiers
import java.nio.file.StandardCopyOption; // Importation de l'énumération StandardCopyOption pour les options de copie

public class CompresseurPdf {

    public static void main(String[] args) {
        // Vérifie que le nombre d'arguments fournis est exactement 2
        if (args.length != 2) {
            System.err.println("Usage: java CompresseurPdf <chemin_du_fichier> <niveau_de_compression>");
            System.err.println("Niveau de compression: 1 (Forte), 2 (Moyenne), 3 (Faible)");
            System.exit(1);
        }
        
        // Récupère le chemin du fichier source depuis les arguments
        String fichierSource = args[0];
        int niveauCompression;

        try {
            // Tente de convertir le second argument en un entier (le niveau de compression)
            niveauCompression = Integer.parseInt(args[1]);
            // Vérifie que le niveau de compression est compris entre 1 et 3
            if (niveauCompression < 1 || niveauCompression > 3) {
                throw new NumberFormatException("Niveau de compression invalide.");
            }
        } catch (NumberFormatException e) {
            // Affiche un message d'erreur si le niveau de compression n'est pas valide
            System.err.println("Le niveau de compression doit être un entier entre 1 et 3.");
            System.exit(1);
            return;
        }

        // Crée une instance de CompresseurPdf
        CompresseurPdf compresseur = new CompresseurPdf();
        try {
            // Tente de compresser le PDF avec le niveau de compression spécifié
            compresseur.compressPdf(fichierSource, niveauCompression);
        } catch (IOException | InterruptedException e) {
            // Affiche une trace de l'exception et un message d'erreur en cas d'échec
            e.printStackTrace();
            System.err.println("Erreur lors de la compression du fichier : " + e.getMessage());
            System.exit(1);
        }
    }

    private void compressPdf(String fichierSource, int niveauCompression) throws IOException, InterruptedException {
        // Vérifie si le fichier source existe et n'est pas un répertoire
        File sourceFile = new File(fichierSource);
        if (!sourceFile.exists() || sourceFile.isDirectory()) {
            System.err.println("Le fichier source n'existe pas ou est un répertoire");
            return;
        }

        // Vérifie si le fichier est en lecture seule et tente de le rendre modifiable si nécessaire
        if (!sourceFile.canWrite()) {
            if (!sourceFile.setWritable(true)) {
                System.err.println("Impossible de rendre le fichier modifiable.");
                return;
            }
        }

        // Utilise un fichier temporaire pour le fichier compressé
        Path fichierCompresseTemp = Files.createTempFile("compressed_", ".pdf");

        // Crée un répertoire temporaire pour Ghostscript
        Path tempDir = Files.createTempDirectory("ghostscript");
        String[] ghostscriptFiles = {"gswin64c.exe", "gsdll64.dll"};

        // Extrait Ghostscript et ses DLLs des ressources
        for (String fileName : ghostscriptFiles) {
            try (InputStream in = CompresseurPdf.class.getResourceAsStream("/ghostscript/" + fileName)) {
                if (in == null) {
                    System.err.println("Le fichier " + fileName + " est introuvable dans les ressources.");
                    return;
                }
                Files.copy(in, tempDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Rend les fichiers exécutables (nécessaire sous Linux/Unix)
        File ghostscriptExecutable = tempDir.resolve("gswin64c.exe").toFile();
        ghostscriptExecutable.setExecutable(true);

        // Définit le niveau de compression PDF basé sur l'argument fourni
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
            default:
                pdfSettings = "/screen";  // Niveau par défaut
                break;
        }

        // Exécute la commande Ghostscript avec le niveau de compression sélectionné
        String[] commande = {
            ghostscriptExecutable.getAbsolutePath(),
            "-sDEVICE=pdfwrite",
            "-dCompatibilityLevel=1.5",
            "-dPDFSETTINGS=" + pdfSettings,
            "-dNOPAUSE",
            "-dQUIET",
            "-dBATCH",
            "-dEmbedAllFonts=false",  // Ne pas incorporer toutes les polices
            "-dSubsetFonts=true",  // Incorporer uniquement des sous-ensembles de polices utilisées
            "-sOutputFile=" + fichierCompresseTemp.toString(),
            sourceFile.getAbsolutePath()
        };

        Process process = new ProcessBuilder(commande)
            .directory(tempDir.toFile())
            .start();

        // Lit la sortie standard et l'erreur du processus
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

        // Attend que le processus se termine
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("Fichier PDF compressé avec succès");
            Files.move(fichierCompresseTemp, sourceFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            System.err.println("La compression du fichier PDF a échoué.");
        }

        // Supprime les fichiers temporaires
        for (String fileName : ghostscriptFiles) {
            new File(tempDir.resolve(fileName).toString()).delete();
        }
        tempDir.toFile().delete();
        fichierCompresseTemp.toFile().delete();
    }
}
