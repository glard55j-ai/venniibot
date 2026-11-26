# KOOMA-I 4.4 — Explorateur cartographique hors ligne

**Version Android :** `4.4-map-explorer-test` (build 21), uniquement sur la branche expérimentale `kooma-cartographie-v4`. La branche stable et les commandes du robot sont inchangées.

## Améliorations regroupées

- **Zoom de 1× à 4×** au moyen des boutons Zoom + et Zoom − ; bouton Cadrer pour revenir à la vue entière.
- **Déplacement par glissement** lorsque la grille est agrandie. Le zoom et le déplacement sont purement visuels.
- **Rotation visuelle par pas de 90°** (0°, 90°, 180°, 270°), sans modification des octets originaux.
- **Trois palettes** : niveaux de gris bruts (par défaut), gris inversés et fausses couleurs. Les fausses couleurs sont décoratives et ne classifient pas les obstacles.
- **Lecture d'une cellule** : toucher la grille indique les coordonnées d'origine (à partir de 0) et la valeur non signée de l'octet (0 à 255), même si la grille a été pivotée ou réduite.
- **Export PNG volontaire** : une boîte de dialogue avertit qu'une image peut révéler l'intérieur d'un domicile, puis l'utilisateur choisit explicitement le fichier avec le sélecteur Android. Aucun export ou envoi automatique n'est créé ; l'image enregistrée correspond à la grille de données brutes dans la palette et la rotation courantes, **pas** au cadrage de zoom de l'écran.

## Limites et précautions

Il s'agit d'un **explorateur de données**, pas encore d'une carte SLAM interprétée. Les couleurs et les valeurs n'indiquent pas de façon confirmée les murs, sols, pièces ou positions. L'orientation physique reste inconnue ; la rotation n'est qu'un outil d'inspection. Les diagnostics `.txt`, les imports JSON hors ligne et la grille synthétique de démonstration fonctionnent toujours.

Le raster est limité à 600 pixels au maximum par côté ; les décodages sont bornés, sans connexion réseau et sans nouveau message envoyé au robot. Le PNG peut contenir des informations privées : ne pas le publier sur un dépôt public.

## Vérification

Le workflow GitHub Actions exécute les tests de la grille, du décodeur Base64/LZ4, des statistiques, de l'extraction TXT et désormais `MapGridExplorerSelfTest` (4 rotations, 3 palettes, lecture de cellules). La compilation Android et la création de l'APK doivent être vérifiées sur la dernière exécution du workflow avant installation. Les tests réels sur l'appareil restent à faire.

## Premier essai sans robot

Dans Cartographie, sélectionner « Tester une grille synthétique », puis Zoom +, Pivoter 90°, Palette, toucher une cellule et éventuellement exporter le PNG. Le visuel est fictif et n'a aucun lien avec une maison.
