# Ferromicon

Il "libro delle regole" del ferroviere: agenda dei turni e controlli contrattuali per il personale mobile Altarail (macchinista e capo treno), per Android, in Kotlin.

Versione **0.0.6** (versionCode 6) · app non ufficiale, non collegata a Altarail né ad altre app. Funziona solo sul telefono: niente account, niente cloud. L'app si collega a internet solo per cercare le nuove versioni su GitHub (si può disattivare dagli Strumenti → Aggiornamenti); l'orario dei treni si scarica dal portale di Regione Lombardia con il browser e poi si importa.

Icona: tomo antico in pelle con fregi dorati e una ruota ferroviaria.

## Requisiti

- **Per compilare:** JDK 17 o 21 e Android SDK con `platforms/android-35` e build-tools 34 . Gradle 8.10.2 viene usato tramite `gradlew`.
- **Per usare l'app:** telefono con Android 8.0 o successivo.

## Funzioni principali

- Agenda mensile con servizi e assenze, controlli sulle regole Altarail (durata, riposi, condotta, notturno, refezione) e stima delle competenze.
- **Recupero festivi** con scadenza a 90 giorni, **perimetri di residenza** per le ore fuori residenza, **riepilogo completo** del mese e del giorno, **dormite** e profilo orario personalizzabile.
- Tema chiaro/scuro e due widget per la schermata iniziale (prossimo servizio, il mio mese).
- **Profilo iniziale** (macchinista o capotreno) e **CID personale**: limiti, condotta o scorta e calendario si adattano a chi usa l'app.
- **Il mio turno**: le giornate del turno dell'impianto compilano l'agenda fino al cambio turno; ogni giorno si può caricare la giornata di default e correggerla (treni in più o in meno) con ricalcolo di tutto.
- **Calendario agente**: dai PDF del calendario degli agenti e dei turni, con il CID o il cognome, genera giornate, riposi e periodi di ferie estivo e invernale (da riposo a riposo).
- **Cedolino e diario di bordo**: il cedolino in PDF imposta profilo e parametri; il diario di bordo mensile si confronta con l'agenda e con il cedolino.
- **Esportazione .ics** (anche su Google Drive) e **sincronizzazione col calendario del telefono**.

> Il modulo `app` non è stato compilato nell'ambiente in cui è stato scritto (senza Android SDK): il primo push su GitHub avvia la compilazione e mostra eventuali errori.

## Compilare, testare, installare (PC Windows)

Dalla cartella del progetto, in PowerShell:

```powershell
.\gradlew.bat :core:test           # esegue i test (92)
.\gradlew.bat :app:assembleDebug   # crea l'APK
```

L'APK viene creato in `app\build\outputs\apk\debug\app-debug.apk`. Con il telefono collegato e il *Debug USB* attivo:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

In alternativa: apri la cartella con Android Studio e premi *Run*. Al primo avvio Android Studio crea `local.properties` con il percorso dell'SDK.

**Con GitHub (senza PC):** il workflow `.github/workflows/build.yml` esegue i test, compila l'APK e lo pubblica nella pagina *Releases* come `ferromicon.apk`.

## Struttura

| Percorso | Contenuto |
|---|---|
| `core/` | Modello, regole, calcoli, import, generatore, calendario: Kotlin puro, senza dipendenze Android, con i test |
| `app/` | Interfaccia Android (View di sistema, nessuna libreria esterna), salvataggio JSON, promemoria |
| `CHANGELOG.md` | Novità per versione |

## Cosa fa

**Agenda**
- Mese con **calendario a griglia** colorato (servizio, violazioni, ferie, riposi, altre assenze), **prossimo servizio** in cima, riepilogo, controlli, elenco delle voci.
- Servizi con **righe treno e attività**: numero, stazioni, orari previsti e **reali**, tipo (condotta, scorta, in vettura, altro). Condotta e condotta continuativa si calcolano dalle righe, con le soste sotto 15' che non interrompono il tratto (Altarail art. 17.7.1). Il ritardo dell'ultimo treno sposta la fine effettiva e i controlli vengono ripetuti sugli orari reali.
- **Materiale rotabile** e **collega di equipaggio** per ogni servizio.
- **Servizi preferiti** (anche con le righe treno), **duplicazione** al giorno o alla settimana dopo, **generatore di turni ciclici**, **importazione da testo** incollato. I servizi già presenti con gli stessi orari non vengono duplicati.
- Assenze e riposi: riposo settimanale, giornata di riposo, recupero festività, ferie, permessi, disponibilità, malattia.
- **Cestino**: ciò che elimini resta recuperabile per 30 giorni, con **annullamento** immediato.
- **Rubrica stazioni** con sigla, telefono (chiamata con un tocco) e note; i nomi vengono suggeriti nelle righe treno.
- **Orario ufficiale Altarail** (Regione Lombardia, Open Data, formato GTFS): dal menu *Orario Altarail* si apre il portale per scaricare lo zip e lo si importa. Nella riga treno, *Cerca il treno nell'orario* trova il treno per numero nel giorno del servizio (o in quello dopo, per i treni dopo mezzanotte), fa scegliere salita e discesa e compila stazioni e orari. Le stazioni dell'orario si possono aggiungere alla rubrica e vengono suggerite. L'app avvisa quando l'orario importato è scaduto. I dati non sono inclusi nell'app: vanno importati.

**Controlli** (contratto Altarail 2012 con l'adeguamento al CCNL 2012, CCNL Mobilità/AF 22/05/2025)
- Prestazione massima diurna e notturna, prolungamento a 10 h una volta tra due riposi settimanali, condotta giornaliera e continuativa, refezione e pause.
- Riposo giornaliero in residenza (11 h; 14 h dopo un servizio terminato tra 00:01 e 01:00, per macchinista e capo treno; 16 h dopo un notturno, 14 h nei turni a zeta), riposo fuori residenza, riposo settimanale (62 h e 2 giornate), con **ora minima di ripresa** per ogni servizio.
- Limiti tra due riposi settimanali, mensili (11 notturni, 5 o 4 RFR, 44 h settimanali, monte ore = giorni × 38 / 7) e annui (79 notturni, 320 h).
- **Qualità dei riposi** del mese: sotto il minimo, al limite, buoni, ampi.

**Conti**
- Saldo ferie e permessi (CCNL art. 30 e 29.4), recuperi festività.
- **Stima delle competenze variabili** con i codici del ruolo paga: incentivo condotta o scorta, controlleria, riserva e formazione, indennità ferie, indennità di turno, notturno, domenicale, festivo, straordinario (sugli orari reali), assenza dalla residenza, pernottamento.
- Riepilogo dell'anno.

**Strumenti**
- Promemoria prima del servizio con l'esito dei controlli, backup su file e ripristino, backup automatici giornalieri (ultimi 14), esportazione CSV e calendario `.ics` (Google Calendar), tema chiaro e scuro.

## Test

`core/src/test/kotlin/it/ferromicon/`: **92 test** in tutto. `RegoleTest.kt`: 44 test su regole, riposi, righe treno e orari reali, cestino, doppioni, qualità dei riposi, ferie, festivi, competenze, importazione, generatore, calendario.

`OrarioTest.kt`: **5 test** sulla lettura dell'orario GTFS (con un file di prova costruito nel test, non con i dati reali): corse e fermate, calendari ed eccezioni, orari oltre le 24, numeri con zeri iniziali, salvataggio e rilettura dell'indice.

Il salvataggio JSON (in `app/`) usa `org.json` di Android e non rientra nei test automatici: è stato verificato a parte con una prova di andata e ritorno.

## Cosa resta da verificare

- **Interpretazioni dell'app** (segnalate anche nel codice): frazioni d'ora degli incentivi calcolate al minuto; 25 giorni di ferie dall'anno in cui si compiono 8 anni; un intervallo di almeno 48 h tra due servizi trattato come riposo settimanale; regola Altarail prevalente sul CCNL per gli orari (CCNL art. 4.7).
- **Orario GTFS non provato con il file vero**: il sandbox di sviluppo non raggiunge il portale; la lettura è provata con un file di prova nello stesso formato.
- **Non presenti:** orari *reali* (ritardi) scaricati da internet (vanno inseriti a mano), import dai portali aziendali, sincronizzazione cloud, tempi accessori per materiale, trasferta, premio di risultato annuale.
- Il CCNL scade il 31/12/2026: i valori del 2027 andranno aggiornati.

Fa sempre fede la busta paga.
