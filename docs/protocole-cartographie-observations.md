# KOOMA-I — premières correspondances du protocole cartographique Venii

Date d'analyse : 2026-11-26. Source : fichier fourni `Venii_1.2.9_APKPure (1).xapk`, `com.veniibot.apk`, analyse statique du `classes.dex` de Venii 1.2.9. Les noms et valeurs ci-dessous viennent des champs entiers de la classe `cn.vange.veniimqtt.config.MqttReportMessageType`, sans exécuter l'APK.

| Identifiant | Nom dans l'APK | Signification indiquée par le nom, NON vérifiée sur le robot |
|---|---|---|
| 888 | `CMD_ID_SYNC_STATUS_REPORT` | Rapport d'état général |
| 1219 | `CMD_ID_LD_MAP` | Données de carte possibles |
| 1220 | `CMD_ID_MAP` | Message de carte |
| 800062 | `CMD_ID_LC_LID_LIDAR_STATUS` | Statut LiDAR |
| 800063 | `CMD_ID_LC_LID_LIDAR_SPEED` | Vitesse LiDAR |
| 800064 | `CMD_ID_LC_LID_LIDAR_DATA` | Données LiDAR |
| 800065 | `CMD_ID_LC_LIDAR_ANGLE` | Angle LiDAR |
| 1599 | `CMD_ID_TRAJ_REPORT` | Trajectoire signalée |
| 1600 | `CMD_ID_LOCATION_REPORT` | Position signalée |
| 1707 | `CMD_ID_COLLECT_MAPS_SWITCH` | Commutateur de collecte de cartes, fonction à déterminer |

Autres éléments trouvés : `N1Cammand.CMD_ID_MAP = 1220`, objets `RobotMap`, `DeviceMap`, `MapEntity`, `DeviceMapData`, `DeviceSweepModel`. Leurs seuls noms ne prouvent ni l'existence d'une carte localement accessible sur le N1 Max ni un format précis.

## Essai physique observé

Dans KOOMA-I, essai de 15 s fourni par l'utilisateur : authentification réussie, réception de l'état `888`, 2 trames JSON / 12 éléments hors authentification ; zéro occurrence de 1219, 1220 et 800062 à 800065. Les autres identifiants reçus comprennent 60, 888, 889, 1225, 1300, 1310, 1422, 1423, 1502, 1503, 1600, 1707.

**Interprétation prudente :** dans `VeniiClient.listenForLidar()`, KOOMA-I émet uniquement la requête de statut `888` puis attend passivement. L'absence de message de carte dans cette fenêtre ne démontre pas que la carte n'existe pas. La réception de 1600 et 1707 ne prouve pas qu'une carte soit disponible.

## Prochaine étape, sans robot

Étudier dans les méthodes de l'APK officiel **comment et quand les commandes 1219 / 1220 et les notifications cartographiques sont utilisées**, et si les données cartographiques sont transmises par TCP local, MQTT ou téléchargement HTTP. Déterminer le schéma du contenu, l'éventuel mécanisme de souscription et les dépendances serveur avant tout essai.

**Ne pas envoyer directement 1219, 1220, 1707, 800062 à 800065 ou d'autres identifiants comme commandes tant que la sémantique et les effets sont inconnus.** Ne pas traiter les données cartographiques, potentiellement révélatrices du plan d'un domicile, comme partageables automatiquement. Aucun décodeur de carte réelle n'est validé à ce stade.
