<div align="center">

# Kiar'a

### L'essentiel, simplement.

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Android](https://img.shields.io/badge/Android-10%2B-green)
![Licence](https://img.shields.io/badge/licence-MIT-orange)

**Un launcher Android minimaliste, gratuit et open source.**

### [Télécharger Kiar'a 1.0.0](https://github.com/LILTHIMPX501/Kiara-Launcher/releases/tag/v1.0.0)

</div>

---

Kiar'a est un lanceur d'applications Android (*launcher*) gratuit, open source, minimaliste et personnalisable, développé en **Kotlin avec Jetpack Compose**.

Son objectif est de proposer un écran d'accueil épuré, rapide à utiliser et centré sur les applications essentielles.

## Fonctionnalités

- **Accueil minimaliste** avec applications favorites personnalisables.
- **Organisation des favoris** : ajout, suppression et modification de leur ordre.
- **Recherche instantanée** parmi les applications installées.
- **Navigation alphabétique A–Z** avec animation Wave.
- **Personnalisation** de la taille de l'alphabet, de l'intensité Wave et des icônes favorites.
- **Thème clair ou sombre**.
- **Horloge** et affichage compact configurables.
- **Fond d'écran personnel** avec réglage de l'assombrissement.
- **Sauvegarde et restauration ZIP** des favoris, des préférences et du fond d'écran.

## Compatibilité

- **Version :** 1.0.0
- **Android minimum :** Android 10 (API 29)
- **Langage :** Kotlin
- **Interface :** Jetpack Compose
- **Identifiant Android :** `fr.kiara.launcher`

Le fonctionnement a été testé sur des appareils Android, notamment pour la navigation, la personnalisation et la restauration des sauvegardes.

## Installation

Pour installer Kiar'a depuis un APK officiel :

1. Télécharger l'APK officiel **app-release.apk** depuis la [Release Kiar'a v1.0.0](https://github.com/LILTHIMPX501/Kiara-Launcher/releases/tag/v1.0.0).
2. Autoriser l'installation depuis cette source si Android le demande.
3. Installer Kiar'a.
4. Sélectionner Kiar'a comme application d'accueil par défaut dans les paramètres Android.

L'emplacement du réglage de l'application d'accueil peut varier selon le fabricant du téléphone.

## Compilation depuis les sources

1. Cloner ce dépôt GitHub ou télécharger son archive.
2. Ouvrir le dossier du projet dans **Android Studio**.
3. Laisser Gradle synchroniser les dépendances.
4. Connecter un appareil Android compatible ou démarrer un émulateur.
5. Compiler et exécuter l'application.

Pour générer un APK Debug :

```powershell
.\gradlew.bat :app:assembleDebug
```

Les fichiers privés de signature ne sont pas inclus dans le dépôt. Toute personne souhaitant distribuer sa propre version Release doit utiliser sa propre clé de signature.

## Sauvegarde et restauration

Kiar'a permet d'exporter sa configuration dans une archive ZIP contenant :

- `settings.json` : préférences d'affichage, favoris et ordre des favoris.
- `background.jpg` : image de fond d'écran, lorsqu'elle est présente et incluse dans l'export.

Pour restaurer une sauvegarde, ouvrir les paramètres de Kiar'a et sélectionner **Importer**.

L'importation remplace la configuration actuelle. Il est recommandé de conserver une copie de sauvegarde avant toute restauration ou désinstallation.

Les applications favorites doivent être installées sur l'appareil de destination pour être utilisables.

## Confidentialité

Kiar'a conserve ses préférences et les informations sur les applications favorites dans le stockage privé de l'application Android.

Les données concernées comprennent notamment :

- Les applications favorites et leur ordre.
- Les préférences d'affichage et de personnalisation.
- Le thème et les réglages de l'horloge.
- L'image de fond d'écran éventuellement choisie.

L'application utilise les mécanismes Android de sélection de documents et d'images pour permettre à l'utilisateur de choisir les fichiers à importer ou exporter.

**Le manifeste Release examiné ne demande pas la permission Android `INTERNET`.** Kiar'a n'a pas besoin de cette permission pour ses fonctionnalités principales.

Les sauvegardes ZIP peuvent contenir une image personnelle et des informations sur les applications favorites. Elles doivent être conservées dans un emplacement sûr et ne pas être publiées sans vérification.

## Sécurité

L'importation ZIP comprend des contrôles de format, de taille et de validation des préférences. Les anciennes sauvegardes compatibles avec le format Kiar'a version 1 restent prises en charge.

Ces protections réduisent certains risques liés aux archives incorrectes ou malveillantes, sans constituer une garantie absolue de sécurité.

Les clés privées de signature, les fichiers de configuration locale et les sauvegardes personnelles ne doivent jamais être ajoutés au dépôt public.

## Licence

Kiar'a est distribué sous **licence MIT**.

Consulter le fichier [LICENSE](LICENSE) pour les conditions complètes.

Copyright © 2026 Kiara Project.

## État du projet

**Kiar'a 1.0.0 — Première version publique disponible.**

Le projet est développé de manière indépendante et a vocation à rester gratuit et open source.

Les contributions, suggestions et signalements de problèmes sont les bienvenus via les [Issues GitHub](https://github.com/LILTHIMPX501/Kiara-Launcher/issues).
