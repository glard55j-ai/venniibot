# KOOMA-I 4.3 — détection de carte dans un diagnostic TXT local

Version expérimentale : `4.3-map-txt-import`, build 20, branche `kooma-cartographie-v4` uniquement.

L'écran Cartographie sait maintenant lire un fichier TXT provenant de la fonction **Exporter les données candidates brutes** de KOOMA-I. Après le résumé des codes, il examine localement les blocs portant exactement les en-têtes `Code 1219`, `Code 1220` et `Code 800064`, sans transmettre ni afficher les contenus bruts dans le journal.

Si un bloc contient un objet JSON de type `MapEntity` avec `width`, `height` et `map` (Base64/LZ4), directement ou dans un objet imbriqué (`content`, `data`, etc.), il peut produire un aperçu **en valeurs d'octets brutes**, sans reconnaître automatiquement les murs, pièces, déplacements ou l'orientation. L'absence de ce format est signalée comme **« Aucune grille complète et décodable »** : cela n'établit pas l'absence de carte dans le robot.

Les imports JSON directs restent pris en charge. Les données sont décodées en mémoire, sans accès Internet, sans commandes supplémentaires, sans envoi de fichier et sans sauvegarde automatique. Le fichier choisi est limité à 6 Mio et la grille décompressée à 4 Mio de cellules.

## Exemple fictif pour essai sans robot

`tools/samples/KOOMA-I-LiDAR-exemple-fictif.txt` reproduit la structure de l'export (en-tête `Code 1219`, contenu JSON, Base64/LZ4) sur **192 cellules artificielles** (16 × 12) qui ne correspondent à aucun domicile. Il permet de vérifier la chaîne d'import sur Android, et fait partie des tests Java automatiques de GitHub Actions.

Pour essayer la chaîne sans connexion au robot :
1. Télécharger ce fichier TXT depuis la branche de développement.
2. Ouvrir **Cartographie** dans KOOMA-I 4.3.
3. Choisir **Analyser un export LiDAR TXT**, puis sélectionner le fichier fictif.
4. Vérifier le titre « GRILLE DÉCODÉE — pas une carte interprétée », l'image en niveaux de gris et les statistiques.

Ces étapes ne valident ni l'accès aux véritables cartes du Veniibot N1 Max ni leur interprétation. **Les commandes de pilotage du robot n'ont pas été modifiées.** Une carte privée ne devrait être exportée ou partagée que volontairement.
