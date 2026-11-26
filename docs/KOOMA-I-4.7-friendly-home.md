# KOOMA-I 4.7 — interface publique, navigation à cinq onglets

Branche expérimentale : `kooma-cartographie-v4` ; version Android `4.7-friendly-home` (build 24).

## Objectif

Nouvel accueil Android à fond blanc, accents vert turquoise, illustration originale du robot et barre inférieure fixe à **cinq onglets**, inspirés du principe d'organisation des captures de référence partagées par l'utilisateur. Il ne s'agit pas de copier les images, personnages ou marques d'autres applications.

## Organisation

- **Carte** : accès direct à `MapActivity` (cartographie expérimentale), zoom, imports, données brutes et diagnostic via le mode avancé.
- **Planning** : saisie volontaire d'un **brouillon d'horaire local uniquement** (répétition illustrative quotidienne). Le robot n'est jamais programmé ni contacté depuis cet écran. Brouillon supprimable.
- **Robot** : accueil central avec illustration originale, état et batterie initialement **non vérifiés**, bouton **« Lire l'état du robot »** (action volontaire : réseau local, authentification et requête connue `888` seulement) et bouton qui ouvre les commandes existantes. État affiché en lecture seule, expiration après 60 secondes.
- **Historique** : accès aux observations techniques existantes, sans prétendre charger les anciens nettoyages du robot.
- **Réglages** : accès au centre technique (ancienne `MainActivity`) et informations sur le fonctionnement local.

L'activité `KoomaHomeActivity` devient le lanceur dans `AndroidManifest.xml`. L'ancienne `MainActivity` reste accessible comme **centre technique**, et `MapActivity` demeure inchangée. Il n'y a aucun nouveau bouton de mouvement ; les procédures de sécurité existantes dans `MainActivity` sont conservées.

## Vérifications et limites

La compilation GitHub Actions vérifie que l'application est compatible avec Android, mais **un essai visuel sur téléphone reste nécessaire** (différents formats d'écran, aspect des icônes, lisibilité).

Les données brutes de la carte, les murs et l'emplacement du robot ne sont pas encore validés sur N1 Max. La programmation des horaires depuis l'application n'est pas implémentée et le planning local ne se synchronise pas avec le robot.

L'écran d'accueil n'interroge jamais le robot sans action explicite. En l'absence de connexion, il affiche un état non vérifié. Le mode avancé conserve les commandes déjà développées et tous les diagnostics.
