import sys  # Module système, utilisé pour les paramètres et les fonctions système
import os  # Module pour les opérations système, comme la manipulation des fichiers et des répertoires
import tempfile  # Module pour la création de fichiers et répertoires temporaires
import subprocess  # Module pour exécuter des sous-processus, permet de lancer Ghostscript
from PyQt5.QtWidgets import (QApplication, QMainWindow, QPushButton, QLabel, 
                             QRadioButton, QButtonGroup, QFileDialog, QVBoxLayout, 
                             QHBoxLayout, QWidget, QLineEdit, QMessageBox)  
# Importation des classes nécessaires de PyQt5 pour créer une interface graphique

from PyQt5.QtCore import QRunnable, QThreadPool, pyqtSlot  
# Importation des classes pour la gestion des threads et des tâches asynchrones dans PyQt5

class CompressTask(QRunnable):  # Classe pour gérer la tâche de compression dans un thread séparé
    def __init__(self, file_path, compression_level, gs_executable, callback):
        super().__init__()
        self.file_path = file_path  # Chemin du fichier à compresser
        self.compression_level = compression_level  # Niveau de compression sélectionné
        self.gs_executable = gs_executable  # Chemin vers l'exécutable Ghostscript
        self.callback = callback  # Fonction de rappel pour signaler la fin de la compression

    @pyqtSlot()
    def run(self):
        # Utilisation d'un fichier temporaire pour le fichier compressé
        with tempfile.TemporaryDirectory() as tempdir:
            tempFile = os.path.join(tempdir, "compressed.pdf")

            # Définition du niveau de compression PDF
            pdfSettings = "/screen" if self.compression_level == 1 else "/ebook" if self.compression_level == 2 else "/printer"

            # Exécution de la commande Ghostscript avec CREATE_NO_WINDOW pour cacher la fenêtre
            commande = [
                self.gs_executable, "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.5",
                f"-dPDFSETTINGS={pdfSettings}", "-dNOPAUSE", "-dQUIET", "-dBATCH",
                "-dEmbedAllFonts=false", "-dSubsetFonts=true",
                f"-sOutputFile={tempFile}", self.file_path
            ]

            startupinfo = subprocess.STARTUPINFO()  # Initialisation des informations de démarrage du processus
            startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW  # Configuration pour cacher la fenêtre du sous-processus

            process = subprocess.Popen(commande, stdout=subprocess.PIPE, stderr=subprocess.PIPE, startupinfo=startupinfo)  # Lancement de la commande Ghostscript
            stdout, stderr = process.communicate()  # Communication avec le processus pour obtenir la sortie standard et les erreurs
            
            if process.returncode == 0:  # Si le processus se termine correctement
                os.replace(tempFile, self.file_path)  # Remplacement du fichier original par le fichier compressé
                self.callback(True)  # Appel du callback avec succès
            else:
                self.callback(False, stderr.decode())  # Appel du callback avec échec et message d'erreur

class CompresseurPdf(QMainWindow):  # Classe principale de l'application PyQt5
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Compresseur PDF")  # Titre de la fenêtre principale
        self.setGeometry(100, 100, 600, 200)  # Dimensions de la fenêtre
        self.threadpool = QThreadPool()  # Pool de threads pour exécuter des tâches asynchrones

        self.filePathField = QLineEdit(self)  # Champ de texte pour afficher le chemin du fichier sélectionné
        self.filePathField.setReadOnly(True)  # Le champ de texte est en lecture seule
        
        fileButton = QPushButton("Fichier", self)  # Bouton pour ouvrir le dialogue de sélection de fichier
        fileButton.clicked.connect(self.openFileDialog)  # Connexion du clic du bouton à la méthode openFileDialog
        
        compressionLabel = QLabel("Niveau de compression : ", self)  # Label pour indiquer la sélection du niveau de compression
        self.compressionOptions = QButtonGroup(self)  # Groupe de boutons radio pour les options de compression

        radioButton1 = QRadioButton("Forte")  # Bouton radio pour la compression forte
        radioButton2 = QRadioButton("Moyenne")  # Bouton radio pour la compression moyenne
        radioButton3 = QRadioButton("Faible")  # Bouton radio pour la compression faible

        self.compressionOptions.addButton(radioButton1, 1)  # Ajout du bouton radio au groupe avec l'ID 1
        self.compressionOptions.addButton(radioButton2, 2)  # Ajout du bouton radio au groupe avec l'ID 2
        self.compressionOptions.addButton(radioButton3, 3)  # Ajout du bouton radio au groupe avec l'ID 3

        radioButton1.setChecked(True)  # Le bouton radio pour la compression forte est sélectionné par défaut

        compressButton = QPushButton("Compresser", self)  # Bouton pour lancer la compression
        compressButton.clicked.connect(self.compressFile)  # Connexion du clic du bouton à la méthode compressFile
        
        self.statusLabel = QLabel("", self)  # Label pour afficher le statut de la compression
        
        fileLayout = QHBoxLayout()  # Layout horizontal pour le bouton de sélection de fichier et le champ de texte
        fileLayout.addWidget(fileButton)
        fileLayout.addWidget(self.filePathField)
        
        compressionLayout = QHBoxLayout()  # Layout horizontal pour les options de compression
        compressionLayout.addWidget(compressionLabel)
        compressionLayout.addWidget(radioButton1)
        compressionLayout.addWidget(radioButton2)
        compressionLayout.addWidget(radioButton3)
        
        mainLayout = QVBoxLayout()  # Layout vertical principal pour organiser les autres layouts et widgets
        mainLayout.addLayout(fileLayout)
        mainLayout.addLayout(compressionLayout)
        mainLayout.addWidget(compressButton)
        mainLayout.addWidget(self.statusLabel)
        
        container = QWidget()  # Conteneur pour les layouts
        container.setLayout(mainLayout)
        self.setCentralWidget(container)  # Définition du conteneur comme widget central de la fenêtre

    def openFileDialog(self):
        options = QFileDialog.Options()  # Options pour le dialogue de sélection de fichier
        filePath, _ = QFileDialog.getOpenFileName(self, "Sélectionner un fichier PDF", "",
                                                  "PDF Files (*.pdf);;All Files (*)", options=options)  # Ouverture du dialogue de sélection de fichier
        if filePath:
            self.filePathField.setText(filePath)  # Mise à jour du champ de texte avec le chemin du fichier sélectionné
            self.selectedFile = filePath  # Sauvegarde du chemin du fichier sélectionné
    
    def getSelectedCompressionLevel(self):
        return self.compressionOptions.checkedId()  # Retourne l'ID du bouton radio sélectionné, représentant le niveau de compression

    def compressionCallback(self, success, error_message=None):
        if success:
            self.statusLabel.setText("Traitement terminé")  # Mise à jour du label de statut en cas de succès
        else:
            QMessageBox.critical(self, "Erreur", f"Echec de la compression : {error_message}")  # Affichage d'un message d'erreur en cas d'échec
            self.statusLabel.setText("")  # Réinitialisation du label de statut

    def compressFile(self):
        if not hasattr(self, 'selectedFile'):
            QMessageBox.warning(self, "Attention", "Veuillez sélectionner un fichier à compresser.")  # Avertissement si aucun fichier n'a été sélectionné
            return

        self.statusLabel.setText("Traitement en cours...")  # Mise à jour du label de statut
        compressionLevel = self.getSelectedCompressionLevel()  # Récupération du niveau de compression sélectionné

        # Chemin vers les fichiers Ghostscript intégrés dans le projet
        gs_path = os.path.join(os.path.dirname(__file__), "ghostscript")
        gs_executable = os.path.join(gs_path, "gswin64c.exe")

        # Créer une tâche de compression et l'exécuter dans un thread séparé
        task = CompressTask(self.selectedFile, compressionLevel, gs_executable, self.compressionCallback)
        self.threadpool.start(task)  # Démarrage de la tâche dans le pool de threads

def main():
    app = QApplication(sys.argv)  # Création de l'application PyQt5
    window = CompresseurPdf()  # Création de la fenêtre principale
    window.show()  # Affichage de la fenêtre
    sys.exit(app.exec_())  # Boucle d'événement principale de l'application

if __name__ == "__main__":
    main()  # Appel de la fonction main() si ce fichier est exécuté en tant que script
