# KOOMA-I — application Android native pour Venii N1 Max (prototype 2.5.2)

## Journal des versions

- **2.5.2-test (build 9)** : changement du nom affiché en KOOMA-I sur Android, dans l’interface et dans le rapport de diagnostic. L’icône de la 2.5.1, l’identifiant d’application, le protocole et les réglages locaux restent inchangés.

- **2.5.1-test (build 8)** : nouvelle icône KOOMA-I fournie par l’utilisateur, redimensionnée à 192 × 192 pixels. Le nom système Venii Local, l’identifiant d’application et le protocole sont inchangés.

- **2.5-test (build 7)** : rapport de diagnostic affichable et copiable manuellement, avec version, dernière tentative réelle, dernière lecture horodatée, valeurs brutes autorisées et états avant/après la dernière commande. Aucun secret, adresse MAC ou SSID exporté ; aucune transmission automatique. Le protocole de commande reste inchangé.

- **2.4-test (build 6)** : carte de nettoyage fictive, télécommande directionnelle et STOP simulés, chronomètre démo, préférences de démonstration sauvegardées localement lors de l’application des réglages. Aucun nouvel ordre réseau ; aucun horaire réel, aucun secret ni autorisation de pilotage sauvegardés. Les panneaux de simulation sont masqués en mode réel.

- **2.3-test (build 5)** : panneau de réglages uniquement en démonstration (aspiration, débit d’eau, mode, volume, horaire fictif), application locale des choix et trace dans l’historique de session. Les options sont illustratives et non validées pour le robot ; aucun nouvel ordre réseau, aucune alarme, aucune programmation réelle. Le panneau est masqué en mode réel.

- **2.2-test (build 4)** : historique horodaté (30 événements en mémoire, effaçables, non persistés), animation de démonstration sans réseau, accès rapide aux commandes, diagnostic local de lecture et erreurs de communication. Les codes de commande et les protections de 2.1 restent inchangés.

- **2.1-test (build 3)** : tableau de bord, indication du mode actif, niveau de batterie marqué non identifié, numéro de version visible, journal des changements dans l'application. Une lecture d'état expire après 60 secondes ; une erreur de connexion ou un changement de mode invalide les données. 
- **2.0-test (build 2)** : démonstration locale, quatre commandes directes, lecture des valeurs brutes horodatée. La suppression des fenêtres de confirmation a été demandée par l'utilisateur ; les commandes réelles nécessitent toujours une lecture d'état et un contrôle physique.
- **1.0** : première version du prototype, conservée sur `main`.

Les commandes 1050, 1060 et 1070 restent non validées physiquement. Le code 60 n'est pas un pourcentage de batterie confirmé. Les valeurs manquantes ne sont pas inventées. Pour tester, connectez le téléphone au réseau VENII, désactivez le mode démo uniquement à proximité du robot, relisez l'état et validez le contrôle physique. Les commandes sont directes : gardez le bouton physique accessible, loin des escaliers.


## Compiler l'APK sans Android Studio

**Méthode conseillée : GitHub Actions**, gratuite pour un petit dépôt personnel selon les limites du compte.

1. Créez un nouveau dépôt GitHub privé, par exemple `venii-local`.
2. Importez **le contenu de ce dossier** à la racine du dépôt, en conservant `.github/workflows/android-apk.yml` (dossier caché).
3. Ouvrez l'onglet **Actions**, workflow `Construire APK Venii Local`, puis cliquez **Run workflow**. Le workflow s'exécute aussi après un push sur `main` ou `venii-local-v2`.
4. À la fin, téléchargez l'artifact `VeniiLocal-debug-APK`, décompressez-le et récupérez `app-debug.apk`.
5. Transférez l'APK sur le téléphone Android et installez-la avec l'autorisation d'installer depuis cette source, si demandée.

Le workflow utilise JDK 17, Android SDK 36, Android Gradle Plugin 8.13.2 et Gradle 8.13. C'est un **APK debug pour essai**, non destiné à la publication sur Play Store. Aucune clé de signature privée utilisateur n'est nécessaire pour cet APK debug.

Alternative : avec JDK 17, Android SDK 36 et Gradle 8.13 déjà installés, exécutez `gradle :app:assembleDebug`.

## Spécifications techniques

- Android natif Java, minSdk 26 / targetSdk 36 / compileSdk 36.
- Android 17 : ce projet cible 36 afin de conserver l'accès LAN implicite pour une installation locale. Si on passe à targetSdk 37, il faudra explicitement gérer la nouvelle autorisation runtime `ACCESS_LOCAL_NETWORK`.
- L'application ne nécessite pas de permission de localisation ni la lecture du SSID. Elle se lie au réseau Wi-Fi présent et utilise l'adresse IP locale fixe du robot.
- La trame chiffrement est inspirée du script authentification fourni par l'utilisateur. La structure `[{"from":"android","to_name":MAC,"from_name":"local","type":"cmd","id":888/1020}]` provient du script V2 et du démarrage observé.
- Le chiffrement AES-ECB est ancien et n'offre pas les propriétés de sécurité modernes ; il est utilisé uniquement pour compatibilité avec le protocole existant du robot sur son Wi-Fi.

## Tests réalisés et limites

- Les tests locaux exécutent `tools/CodecSelfTest.java` sur une JVM pour valider l'encodage et le décodage des trames.
- Des compilations APK V2 ont réussi dans GitHub Actions et le mode démo a été testé sur le téléphone. Les nouvelles commandes et l'interprétation de la télémétrie restent à valider sur le robot.
- Le code `1020` est validé physiquement dans le parcours précédent ; `888` est validé en réponse JSON. `1060`, `1070` et `1050` ne le sont pas.

### Vérification hors Android

```bash
mkdir -p /tmp/venii-codec
javac -d /tmp/venii-codec app/src/main/java/com/veniilocal/VeniiFrameCodec.java tools/CodecSelfTest.java
java -cp /tmp/venii-codec CodecSelfTest
```
