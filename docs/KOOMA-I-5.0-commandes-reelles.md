# KOOMA-I 5.0 — Contrôle réel uniquement (sans mode démo)

Branche : `kooma-cartographie-v4`. Version Android : `5.0-real-controls`, code 27.

## Changements

- Retrait de la simulation de déplacement, du chronomètre fictif, de la fausse carte, des réglages simulés et du commutateur « Mode démonstration » de l'activité technique.
- Retrait du bouton de génération d'une carte de démonstration dans l'explorateur cartographique ; l'écran ne montre plus de carte artificielle. Il affiche « Aucune carte chargée » tant qu'aucune vraie grille n'a été importée. L'accueil « Carte » n'affiche plus de faux plan.
- Accueil « Robot » **plus compact** et ajout de quatre boutons liés aux **vraies commandes locales** : démarrer (`1020`), pause (`1060`), reprendre (`1070`) et retourner à la base (`1050`).
- Les mêmes quatre commandes sont accessibles depuis le centre technique. Les boutons restent cliquables pendant les périodes d'inactivité ; si une condition de sécurité n'est pas remplie, l'application explique comment procéder au lieu d'envoyer un ordre.
- Les boutons du robot demandent une **lecture d'état valide de moins de 60 s**, une vérification manuelle du pare-chocs, des capteurs, des escaliers et du sol, puis une **confirmation explicite** avant chaque ordre.
- Après confirmation, `VeniiClient.commandOnce` effectue une seule transmission du code autorisé, après lecture du statut par le protocole existant, puis tente une lecture d'état ultérieure. Aucun essai automatique répété et aucune autre commande motrice ajoutée.
- Après une commande sur l'accueil, le dernier état est invalidé : une **nouvelle lecture** est nécessaire avant l'ordre suivant. Une réponse absente ou incertaine n'est jamais annoncée comme un succès physique.
- Toutes les fonctions de diagnostic LiDAR, d'import TXT/JSON et de suivi technique sont conservées.

## Ce qui n'a pas été ajouté

Aucune programmation distante, nettoyage de zones, trace LiDAR réelle ni bouton d'arrêt d'urgence à distance. Les « favoris » et « zones » de l'accueil restent des **brouillons locaux non exécutables**, et leurs boutons d'édition n'envoient pas de commande.

## Premier test sur robot

Installer sur le téléphone seulement après compilation réussie. Rester près du N1 Max, charger la batterie, dégager le sol et vérifier le pare-chocs / les capteurs / les escaliers. Connecter le téléphone au Wi-Fi propre au robot. Depuis l'onglet Robot, utiliser « Actualiser l'état du robot », contrôler la valeur, cocher la sécurité, puis toucher l'une des commandes et confirmer l'envoi. **Observer physiquement l'effet et ne pas renvoyer immédiatement un ordre si la réponse est incertaine.**

L'absence de mode démo ne prouve pas encore que chaque commande est exécutée correctement sur le matériel ; la compilation est une validation du code, non une validation matérielle.
