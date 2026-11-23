# Laufkarten-Rückmeldung – Android-App

Android-App für die Rückmeldung von Arbeitsgängen an den Laufkarten-Server.
Sie zeigt die Rückmelde-Seite des Servers im Vollbild und bringt einen Barcode-Scanner mit
(Google Code Scanner über die Google Play-Dienste – keine Kamera-Berechtigung, funktioniert auch ohne HTTPS).

## APK bauen lassen (GitHub)

1. Auf github.com ein neues Repository anlegen (z. B. `laufkarten-android`, gerne **privat**).
2. Den Inhalt dieses Ordners hochladen („Add file“ → „Upload files“, alle Dateien und Ordner
   inkl. `.github` hineinziehen) und „Commit changes“.
   Hinweis: Ordner, die mit einem Punkt beginnen (`.github`), zeigt Windows evtl. nicht an –
   dann den Ordner per Git hochladen oder den Workflow über „Actions“ → „New workflow“ →
   „set up a workflow yourself“ anlegen und den Inhalt von `.github/workflows/apk-bauen.yml` einfügen.
3. Unter **Actions** startet „APK bauen“ automatisch (ca. 3–5 Minuten).
4. Fertige APK: rechts unter **Releases** → neueste Version → `Laufkarten-Rueckmeldung-….apk`.
   (Alternativ im Workflow-Lauf unter „Artifacts“.)

Jede Änderung im Repository erzeugt automatisch eine neue Version. Die APK ist immer mit demselben
Schlüssel signiert (`app/laufkarten.keystore`), Updates lassen sich daher einfach drüber installieren.

## Auf dem Handy installieren

1. APK auf dem Handy herunterladen (Releases-Seite im Browser öffnen) oder per USB/E-Mail übertragen.
2. Öffnen → bei Nachfrage „Installation aus dieser Quelle zulassen“.
3. Beim ersten Start den Server eingeben, z. B. `DATENCNC` oder `192.168.1.20`
   (`http://` und `:8080` werden ergänzt). Ändern später über ⚙ oben rechts.

## Funktionen

- Vollbild, Bildschirm bleibt an
- Barcode-Scan über den Button „Kamera“ (Code 128, Code 39, QR)
- Auftragsdaten (PDF/DXF/STEP) werden heruntergeladen und mit der passenden App geöffnet
- Server nicht erreichbar → Seite mit „Erneut versuchen“ / „Server-Adresse ändern“
- Updates der Rückmelde-Seite kommen automatisch vom Server, die APK muss dafür nicht neu gebaut werden

Voraussetzung: Android 7 oder neuer, Google Play-Dienste (für den Scanner). Geräte mit eingebautem
Scanner (Tastatur-Modus) funktionieren auch ohne Play-Dienste.

## Hinweis zum Schlüssel

`app/laufkarten.keystore` (Passwort `laufkarten`) ist für die firmeninterne Verteilung gedacht. Das Repository
deshalb **privat** halten. Für den Play Store wäre ein eigener, geheim gehaltener Schlüssel nötig.
