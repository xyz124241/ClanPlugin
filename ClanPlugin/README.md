# ClanPlugin

Clan-Plugin für Paper-Server (Java 21) mit LuckPerms-Anbindung.

## Bauen

Voraussetzung: JDK 21 und Maven.

```
mvn clean package
```

Die fertige Datei liegt danach in `target/ClanPlugin.jar` und kommt in den `plugins`-Ordner des Servers.
Falls dein Server eine andere Paper-Version nutzt, ändere `paper.version` in der `pom.xml`.

## Befehle

| Befehl | Wer | Was |
|---|---|---|
| `/clan` | alle | Ohne Clan: Hilfe. Im Clan: öffnet das Clan-Menü |
| `/clan create <name>` | alle ohne Clan | Clan gründen (1–7 Zeichen, Farben per MiniMessage) |
| `/clan invite <spieler>` | Recht „Spieler verwalten“ | Spieler einladen |
| `/clan accept [clan]` | alle ohne Clan | Einladung annehmen |
| `/clan delete` + `/clan delete confirm` | Owner | Clan löschen (Ender-Chest-Inhalt geht an den Owner) |
| `/clan leave` | Mitglieder (nicht Owner) | Clan verlassen |

Die Permission `clan.use` ist standardmäßig für alle Spieler aktiv, unabhängig vom LuckPerms-Rang.

## Clan-Menü

- **Clan Home setzen / Zum Clan Home** – Home an der eigenen Position setzen bzw. hinteleportieren
- **Clan Ender Chest** – gemeinsames Lager, wird gespeichert
- **Name Tag** – neuen Namen/Farben im Chat eingeben (`cancel` zum Abbrechen)
- **Mitglieder** – Übersicht mit Owner, Admin, Mod, Member. Klick auf einen Spieler: Owner kann Ränge vergeben; wer „Spieler verwalten“ darf, kann rangniedrigere Spieler rauswerfen
- **Einstellungen** – Owner legt pro Aktion den Mindestrang fest (Spieler verwalten, Ender Chest öffnen, Home setzen, Home teleportieren, Namen ändern) und schaltet Friendly Fire an/aus

## Clan-Name

Der Name wird in MiniMessage geschrieben, z. B. `<red>Wolf`, `<gradient:red:blue>Wolf</gradient>` oder `<rainbow>Wolf`.
Gezählt wird der Klarname ohne Farbcodes: 1 bis 7 Zeichen (Buchstaben, Zahlen, `_`). Klick-/Hover-Tags sind bewusst nicht erlaubt.

## Tab-Liste und Nametag

Reihenfolge: `[Clan] [LuckPerms-Rang] Spielername`, danach der Ping. Das gilt in der Tab-Liste und über dem Kopf.
Der Clan-Prefix wird nicht in den Chat eingefügt. Die Tab-Reihenfolge richtet sich nach dem Gewicht der LuckPerms-Primärgruppe.

Dafür nutzt das Plugin Scoreboard-Teams auf dem Haupt-Scoreboard. Andere Plugins, die ebenfalls Teams/Prefixe setzen (z. B. TAB), können sich damit überschneiden.

## Konfiguration

`plugins/ClanPlugin/config.yml`: maximale Mitglieder, Ender-Chest-Größe, Einladungsdauer, Format des Clan-Prefix und Schreibweise der LuckPerms-Prefixe (`LEGACY` oder `MINIMESSAGE`).
Die Daten liegen in `plugins/ClanPlugin/clans.yml`.
