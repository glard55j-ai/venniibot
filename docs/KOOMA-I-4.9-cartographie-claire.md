# KOOMA-I 4.9 — cartographie réorganisée et sélection visuelle de zones

Version : `4.9-map-redesign` (code Android 26). Branche expérimentale `kooma-cartographie-v4` uniquement.

## Nouveau parcours cartographique

L'écran `MapActivity` a été réorganisé dans le style clair et vert turquoise de l'accueil KOOMA-I 4.7/4.8 :

- En-tête lisible, retour explicite, statut des données.
- Grand panneau central d'aperçu ; lorsque aucune grille n'est chargée, un écran vide neutre remplace l'ancien plan de démonstration fictif.
- Outils visuels regroupés : zoom, cadrage, rotation de 90°, trois palettes de couleurs, export PNG volontaire.
- Import JSON, diagnostic LiDAR TXT et grille synthétique proposés dans un panneau distinct.
- Lecture brute d'une cellule en touchant la grille.
- Résultat d'analyse toujours visible ; histogramme des octets et détails du format regroupés dans un panneau technique que l'utilisateur peut déplier.

## Étiquettes de zones, sans surface physique

L'écran liste les **étiquettes de zones enregistrées en brouillon local** dans `KoomaDraftStore` et permet d'en sélectionner une visuellement. La sélection ne modifie **aucune zone du plan**, n'associe aucune coordonnée, n'enregistre aucun rectangle cartographique, n'interdit aucun déplacement et **n'envoie aucune commande au N1 Max**. Un message explicite l'indique sous les étiquettes.

« Gérer les zones dans KOOMA-I » revient directement à l'onglet Carte de l'accueil (par un Intent interne avec `open_tab=0`). L'activité d'accueil sait désormais recevoir cette navigation et rafraîchir les brouillons après retour.

## Conservation de l'existant

Le code de décodage `MapGridDecoder`, `MapGridRasterizer`, `MapDocumentParser`, le rapport LiDAR TXT et le transfert direct temporaire `MapCandidateMemory` sont conservés. Les commandes réelles de déplacement dans `MainActivity` ne sont pas modifiées.

Les grilles, lorsqu'elles existent, restent des **valeurs d'octets sans interprétation confirmée** des murs, des sols ou de l'orientation. Il n'existe à ce stade aucun nettoyage réel par zone.

## Validation

La compilation GitHub Actions doit réussir avant de distribuer l'APK. Une compilation réussie ne confirme pas à elle seule le bon rendu des écrans sur un appareil : un test visuel et tactile Android reste nécessaire.
