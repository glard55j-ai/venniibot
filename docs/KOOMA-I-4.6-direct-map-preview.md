# KOOMA-I 4.6 — aperçu direct depuis le diagnostic LiDAR

Version Android : `4.6-direct-map-preview` (build 23). Branche expérimentale : `kooma-cartographie-v4`.

## Ce qui change

L'utilisateur n'a plus besoin d'exporter un fichier TXT pour examiner les **messages cartographiques candidats réellement reçus** durant son diagnostic réseau.

1. Dans le tableau de bord, effectuer, si souhaité, une **écoute LiDAR de 15 ou 45 secondes**. L'écoute effectue la découverte et l'authentification habituelles, puis envoie **uniquement la demande d'état connue `888`** avant de recevoir les réponses.
2. Si et seulement si le rapport contient un message `1219`, `1220` ou `800064` dont le contenu brut a pu être conservé dans les limites de taille existantes, le bouton **« Examiner les données reçues dans Cartographie »** s'active.
3. Ce bouton transfère **uniquement après une action explicite** les données candidates vers l'écran Cartographie, via `MapCandidateMemory`, **sans Intent contenant un gros fichier et sans écriture sur disque**.
4. L'écran reprend le décodage déjà disponible (détection d'objet `MapEntity` éventuel, largeur, hauteur, Base64, LZ4, nuances de gris brutes, statistiques et explorateur). Si aucun message ne contient une grille décodable, il affiche le rapport au lieu d'inventer une carte.

## Limites de confidentialité

- Transfert en mémoire **une seule fois** ; durée de vie maximale : **5 minutes**, taille limitée à **1 600 000 caractères**. La donnée est consommée à l'ouverture de l'écran. Une fermeture forcée du processus peut faire perdre le transfert.
- Aucun upload, stockage permanent, partage automatique, collecte de coordonnées explicites ou commande supplémentaire du robot.
- Les éventuels contenus cartographiques restent également temporairement disponibles dans le `LidarReport` de l'activité principale pour l'export brut manuel préexistant. Les commandes de déplacement du N1 Max ne sont pas modifiées.
- Une carte peut dévoiler le plan d'un domicile. Ne partager un export TXT ou PNG qu'intentionnellement.

## Ce que cela ne prouve pas

Le dernier essai physique connu n'avait reçu **aucun message** `1219`, `1220` ou `800064`. Dans cette situation, le nouveau bouton restera indisponible. Nous ne disposons toujours pas d'une vraie carte du N1 Max ; l'application ne l'obtient **pas** à distance ou en envoyant un ID de commande cartographique non vérifié.

L'analyse statique de Venii 1.2.9 a confirmé un chemin de décodage Base64 → LZ4 et un chemin possible de téléchargement depuis `resources.veniibot.com`. En revanche, les conditions d'émission des messages de carte, une éventuelle souscription MQTT et le sens des valeurs d'occupation n'ont pas été identifiés avec assez de certitude pour les reproduire.

## Validation

`MapCandidateMemorySelfTest` vérifie l'échange unique, les entrées vides, la limite de taille, le remplacement et l'effacement. GitHub Actions exécute ce nouveau test avant la compilation Android. La réussite GitHub ne remplace pas une vérification du parcours avec le téléphone et le robot.
