# KOOMA-I 4.5 — diagnostic cartographique approfondi

Branche expérimentale : `kooma-cartographie-v4`. Version Android `4.5-map-diagnostic` (build 22).

## Nouveautés en lecture seule

- Deux durées d'écoute facultatives : **15 secondes** (rapide) et **45 secondes** (prolongée).
- Le réseau reste celui du robot Veniibot N1 Max, avec la découverte UDP, l'authentification `67`, et **la seule demande de statut connue `888`** envoyée avant d'écouter. Aucun message inconnu de commande LiDAR ou de mouvement n'est émis.
- Le rapport cartographique compte et décrit la **forme**, sans valeur, des messages candidats `1219`, `1220`, `800062` à `800065`, ainsi que des signaux auxiliaires `1599` (trajectoire supposée), `1600` (position supposée) et `1707` (fonction supposée de collecte). Les descriptions sont des hypothèses tirées des noms de l'ancienne APK, pas des interprétations validées sur le N1 Max.
- Pour chaque code reconnu, les types possibles indiqués sont : objet JSON (nombre de champs), tableau JSON (longueur), nombre, booléen, texte ou texte JSON probable. Le rapport peut aussi indiquer la taille en octets, et compte les occurrences.
- Un bouton **« Copier le résumé LiDAR (sans données privées) »** permet de copier manuellement seulement ce résumé. Aucune coordonnée, valeur de contenu, SSID, adresse MAC, jeton d'authentification ni plan n'est inclus dans ce résumé. Le presse-papiers Android reste néanmoins accessible aux applications selon les règles du système.

## Confidentialité et limites

Les contenus des codes `1219`, `1220` ou `800064` continuent à être retenus temporairement **uniquement** pour permettre un export brut manuel distinct, limité en taille et précédé d'un avertissement. Aucun transfert automatique n'est effectué.

La présence des signaux `1600` ou `1707` **ne prouve pas** que le robot a fourni une carte. Même après 45 secondes, une absence de `1219`/`1220`/`800064` ne prouve pas que la carte n'existe pas. L'interprétation du protocole et le rendu réel des murs ne sont pas encore validés.

Utiliser l'observation uniquement avec le robot accessible, près de sa base et sans lancer de nouvelle commande physique. Si l'application retourne un état, cela n'autorise pas automatiquement le pilotage : relire l'état avant toute commande réelle.

Les outils hors ligne d'import JSON/TXT, zoom, rotation, palettes, valeurs de cellule et export PNG de KOOMA-I 4.4 restent présents. La branche principale `main` et les codes de commande de déplacement n'ont pas été changés.

## Vérification

GitHub Actions doit réussir avant d'installer l'APK. Une réussite de compilation ne vaut pas validation physique sur le robot.
