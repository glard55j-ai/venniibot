# KOOMA-I 4.8 — Favoris et zones en brouillon local

Branche : `kooma-cartographie-v4`. Version Android : `4.8-favorites-zones` (build 25).

## Interface inspirée des captures, sans reproduire leur contenu

- Les **cinq onglets** de la version 4.7 sont conservés (Carte, Planning, Robot, Historique, Réglages).
- **Robot → Mes favoris** : créer jusqu'à six favoris, choisir un nom, un type de routine imaginée (partout ou zones à définir) et une couleur (vert, bleu, orange, violet ou sable). Modifier ou supprimer chaque brouillon.
- **Carte → Mes zones** : créer jusqu'à six étiquettes de pièces/zones, marquées « zone à nettoyer » ou « zone à éviter », avec une couleur. Modifier ou supprimer.
- **Planning** : affiche le nombre de favoris disponibles comme élément de présentation, sans lancer de tâche.
- Les cartes sont blanches ou pastel, avec arrondis, accents turquoise et boutons lisibles, dans l'esprit des captures.

## Limites de sécurité et de compatibilité

**Ni les favoris ni les zones ne sont envoyés au robot.** Les types « Partout », « Zone à nettoyer » et « Zone à éviter » sont des **libellés de projet**, pas des commandes. Aucune géométrie de zone, zone interdite, horaire opérationnel ni position du robot n'est créée ou appliquée à une véritable carte.

Les brouillons sont sauvegardés **localement sur le téléphone** dans les préférences privées d'Android (`kooma_ui_drafts_v1`), avec taille et nombre limités. Pas de compte cloud ni de transfert automatique ; `android:allowBackup=false` est conservé.

Le comportement antérieur des commandes réelles et du diagnostic LiDAR demeure dans le Mode avancé et dans l'explorateur de carte expérimental. Les activités existantes n'ont pas été modifiées.

## Vérification

Compilation Android sur GitHub Actions obligatoire avant installation. L'apparence et les boîtes de dialogue doivent ensuite être contrôlées sur un appareil : la réussite d'une compilation ne valide pas les écrans tactiles ni une vraie opération de nettoyage.
