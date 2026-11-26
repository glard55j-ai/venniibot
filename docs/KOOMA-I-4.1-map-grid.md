# KOOMA-I 4.1 — aperçu d’une grille cartographique hors ligne

Branche de développement : `kooma-cartographie-v4`. Version Android `4.1-map-grid-test` (build 18).

## Modifications

L’écran **Cartographie** est maintenant relié au décodeur expérimental `MapGridDecoder` et à `MapGridRasterizer`.

- **Tester une grille synthétique** : décode un bloc LZ4/Base64 créé localement, puis affiche une petite grille fictive, sans robot.
- **Importer une carte JSON locale** : demande volontairement un fichier avec trois champs de premier niveau `width`, `height` (entiers positifs) et `map` (chaîne Base64 de données compressées dans un bloc LZ4). `resolution`, `x_min` et `y_min` sont affichés comme nombres *bruts* s’ils sont présents.
- **Analyser un export LiDAR TXT** : conserve le lecteur d’exports antérieur ; un export TXT n'est **pas** automatiquement une carte.
- **Revenir à la carte fictive** pour effacer l’affichage de la grille importée.

Le moteur dessine une **image en niveaux de gris de la valeur brute de chaque octet** (0 = noir, 255 = blanc), sans décider si une valeur signifie mur, sol, passage libre ou inconnu. L’ordre des lignes, l’orientation, la résolution physique et les couleurs officielles restent à vérifier avec un fichier réel.

L'interface utilise le sélecteur de fichiers Android et un traitement sur un fil de fond. L’import est limité à 6 Mio, le décodeur à 4 Mio de cellules et le rendu à 600 px au maximum par côté. Aucune connexion, commande robot, collecte réseau, téléchargement externe, sauvegarde automatique ou partage du plan n’a été ajouté à cet écran.

## Compatibilité de fichier — non validée sur un export du N1 Max

Exemple de structure attendue, **schéma provisoire et illustratif** :

```json
{
  "width": 16,
  "height": 12,
  "map": "<chaîne Base64 d'un bloc LZ4 brut>",
  "resolution": 0.05,
  "x_min": 0,
  "y_min": 0
}
```

La méthode observée dans l'APK Venii 1.2.9 lit `MapEntity.getWidth()`, `getHeight()`, `getMap()`, `getResolution()` et `getX_min()`/`getY_min()`, puis décode Base64 et décompresse LZ4. Cela ne prouve **pas** que les exports disponibles ont exactement cette structure JSON ni qu'une carte locale soit accessible à ce stade.

## Tests

Trois contrôles Java hors Android s'exécutent automatiquement *avant* la compilation de l’APK : `MapPayloadInspectorSelfTest`, `MapGridDecoderSelfTest` et `MapGridRasterizerSelfTest`. Le résultat du workflow doit être vérifié pour déclarer une compilation réussie.

**Pour l’instant, seul le rendu de cellules arbitraires est prêt.** Une vraie carte nécessite un échantillon autorisé et local, l'interprétation documentée des octets et la validation de l'orientation. Ne pas essayer d'envoyer des identifiants cartographiques inconnus au robot.
