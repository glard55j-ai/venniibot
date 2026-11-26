# KOOMA-I — Analyse du format de carte de Venii 1.2.9

Date : 26 novembre 2026. Analyse **statique** des DEX de l'archive `Venii_1.2.9_APKPure (1).xapk` déjà fournie par l'utilisateur ; aucun test sur le robot ni sur les serveurs Venii.

## Ce qui est établi par les appels présents dans le DEX

La méthode `Lc/y/c/k/k;->a(Lcn/vange/veniimqtt/entity/MapEntity;)Lcom/veniibot/mvp/model/entity/RobotMap;` :

1. Lit `getResolution()`, `getWidth()`, `getHeight()`, `getX_min()`, `getY_min()` et `getPathId()`.
2. Lit `getMap()`, qui retourne une chaîne de caractères.
3. Appelle `android.util.Base64.decode(...)`.
4. Multiplie `width × height` (`mul-int` visible dans cette méthode).
5. Appelle `Lc/y/c/k/r;->a([B,I)[B` avec la taille calculée.
6. Place le tableau d'octets obtenu dans `RobotMap.setMapDataBytes(...)`.

La méthode `Lc/y/c/k/r;->a([B,I)[B` appelle `net.jpountz.lz4.LZ4Factory.fastestInstance().fastDecompressor().decompress(...)`.

**Conclusion fondée :** au moins un format de carte pris en charge par l'application suit le chemin **Base64 → bloc LZ4 → grille d'octets de dimensions largeur × hauteur**. Le sens des valeurs de chaque cellule, l'orientation de la grille, les couleurs et la compatibilité avec une carte particulière du N1 Max restent non établis.

## Transport et stockage : réseau local ou serveur ?

La méthode `Lc/y/g/b/b/p$b$a;->a()V` lit `MapEntity.getUrl()` et construit une URL en préfixant `http://resources.veniibot.com`. Elle copie l'URL dans `DeviceMapCmd.setDownUrl()` et le contrôle MD5 obtenu par `MapEntity.getMd5()` dans `DeviceMapCmd.setMd5()`.

Dans `DeviceMapDetailActivity$b$a;->a()V`, un autre chemin de téléchargement utilise `DeviceMap.getHistory_file_path()` et `DeviceMap.getMaps_md5()`.

**Conclusion limitée :** l'application officielle dispose d'un mécanisme de téléchargement de cartes/archives et de contrôle MD5, possiblement dépendant d'un serveur. Cela **ne prouve pas** qu'il soit indispensable pour toutes les cartes, ni que le robot ne puisse jamais transmettre les mêmes données en local. Aucun accès réseau au serveur n'a été réalisé pendant cette analyse.

## Implémentation expérimentale dans KOOMA-I

- `MapGridDecoder.java` : décodeur **sans réseau**, sans fichiers et sans commandes robot, qui accepte une chaîne Base64, une largeur et une hauteur, décompresse un bloc LZ4 et vérifie strictement la longueur finale `width × height`.
- Mémoire bornée à **4 Mio de cellules** ; rejet des entrées invalides ou hors limites.
- `tools/MapGridDecoderSelfTest.java` : cas synthétiques valides et invalides (littéraux, répétition, Base64 invalide, dimensions incorrectes, offset nul et longueur tronquée).
- Les tests Java ont été exécutés localement avec succès ; les contrôles GitHub Actions sont séparés et doivent être vérifiés avant de déclarer la compilation Android réussie.
- Le décodeur **n'est pas encore relié à un rendu de carte réelle** faute d'échantillon confirmé. L'écran actuel reste explicitement fictif.

## Pour l'étape suivante

1. Obtenir, de façon consentie et locale, un véritable **exemple de message MapEntity**, depuis le robot si disponible ou un export de l'ancienne application.
2. Valider le schéma et décompresser le contenu dans le module hors ligne, sans exposer automatiquement le plan du domicile.
3. Identifier le sens des valeurs de cellule puis seulement créer un rendu de carte réelle ; conserver une séparation visible entre démonstration et carte réelle.
4. Ne pas envoyer de commandes cartographiques inconnues avant d'avoir identifié leur rôle exact, leur canal et leurs effets.

L'essai KOOMA-I précédent a reçu 888 et plusieurs autres identifiants mais **aucun 1219/1220/800064** pendant 15 secondes ; ce résultat ne permet pas de conclure sur l'accès aux cartes.
