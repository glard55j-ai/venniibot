# KOOMA-I 4.2 — analyse hors ligne des octets de carte

Cette version expérimentale est sur la branche `kooma-cartographie-v4`. Version Android : `4.2-map-stats-test` (build 19).

Après décodage Base64/LZ4, l'écran cartographique affiche toujours la grille en **niveaux de gris bruts**. Il présente maintenant un résumé *descriptif* des octets :
- nombre de cellules ;
- nombre de valeurs distinctes (0 à 255) ;
- huit valeurs les plus fréquentes, leurs effectifs et pourcentages (octets non signés).

Le bouton **Tester une grille synthétique** calcule lui aussi ces statistiques, sans téléphone connecté au robot. Le bouton **Revenir à la carte fictive** efface l'aperçu et le résumé. Les imports TXT restent séparés de l'import JSON cartographique.

Ces valeurs **ne sont pas encore identifiées comme murs, sols, obstacles, zones accessibles ou inconnu**. Aucune classification n'est effectuée et aucun plan privé n'est partagé, enregistré automatiquement ou envoyé en ligne. Les commandes de pilotage du Veniibot N1 Max demeurent inchangées.

Fichiers concernés : `MapGridStatistics.java`, `tools/MapGridStatisticsSelfTest.java`, `MapActivity.java`, `.github/workflows/android-apk.yml`, `app/build.gradle`. Les tests statistiques s'exécutent avant la compilation Android. La compilation doit être contrôlée dans GitHub Actions.

Prochaine étape nécessitant une entrée réelle : importer un fichier `MapEntity` valide que le propriétaire choisit de fournir, puis comparer ses valeurs au rendu officiel pour déterminer leur sens et l'orientation de la grille. L'absence de 1219 / 1220 / 800064 pendant l'écoute précédente de quinze secondes ne prouve pas que la carte est inaccessible.
