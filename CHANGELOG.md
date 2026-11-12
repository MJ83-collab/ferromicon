# Novità

Versione 0.0.9 (versionCode 9). Ogni voce è una funzione aggiunta; il modulo `app` si controlla compilando su GitHub, il modulo `core` ha 105 test.

## 0.0.9
- Aggiornamenti: questa è la prima release pubblicata dopo la 0.0.6. Include anche le modifiche della 0.0.7 e della 0.0.8, che non erano state pubblicate su GitHub. Da ora il controllo aggiornamenti trova le versioni nuove.
- Compilazione automatica: ogni push su `main` compila l'APK, esegue i test delle regole e pubblica la release con `ferromicon.apk` e `versione.json`.

## 0.0.8
- Solidità: dove il nome di un file non si legge, l'app non ignora più l'errore in silenzio; lo registra nel log.
- Nessuna modifica visibile alle funzioni. Il resto dei punti del report di solidità è descritto in `report.md`.

## 0.0.7
- Il workflow si ferma se la versione esiste già su GitHub: un push senza aumento di versione non sostituisce più la release.
- Correzione del changelog: la firma fissa parte dalla 0.0.7 (vedi sopra per la 0.0.6).
- Strumenti → «Importa file»: un solo pulsante per zip dell'orario (.zip), calendario (.ics) e backup (.json). I PDF non si riconoscono dal nome: l'app chiede a quale sezione appartengono.
- Calendario agente: la lettera estiva scelta per il 2026 scorre ogni anno nella sequenza D-A-C-E-F-B, e per ogni anno si calcolano il periodo estivo e quello invernale della stessa lettera. La Pasqua si calcola dalla data della festività. Le date fisse valgono finché l'avviso aziendale non cambia: vanno verificate ogni anno.
- Margini in alto: il testo delle schermate (Strumenti, Riepilogo, Agenda, Diario, Cedolini) non finisce più sotto la barra di stato.

## 0.0.6
- Strumenti → Aggiornamenti: la voce mostra la versione installata; se è uscita una versione più recente, la riga dice «DISPONIBILE» con il numero. «Controlla aggiornamenti» indica versione installata e ultima online.
- Cedolini: il messaggio «Non letti» indica per ogni file quanto testo è stato estratto dal PDF e quali campi mancano (anno, mese, voci).

## 0.0.5
- Calendario agente: nuovo pulsante «3. Carica il calendario rotativo dei riposi (PDF)». Con il rotativo i riposi e le ferie estive si calcolano anche oltre la settimana del PDF degli agenti, a partire dalla posizione dell'agente nel ciclo. Le giornate di lavoro oltre la settimana restano senza orario se il PDF dei turni non le copre.

## 0.0.4
- Cedolini e CU si caricano dal telefono o dal PC (uno o più PDF insieme). Nessun collegamento ai siti aziendali: l'app non scarica nulla dal portale del personale.
- Schermate secondarie con lo stesso aspetto della principale.
- Widget nuovi: **Oggi** (servizio o assenza di oggi e di domani) e **Prossimo riposo** (giorni al prossimo riposo o alle ferie).
- Lettura dei cedolini verificata su mesi del 2015–2026 (ordinari e tredicesime).

## Aggiornamento automatico (0.0.3)
- All'avvio (al massimo ogni 12 ore) l'app controlla se su GitHub c'è una release più recente; se sì propone «Scarica e installa». Android chiede sempre la conferma finale.
- Strumenti → Aggiornamenti: controllo immediato e interruttore del controllo automatico.
- Ogni push su GitHub crea la release `v<versione>` con `ferromicon.apk` e `versione.json`: per pubblicare una novità basta alzare la versione e caricare.
- La chiave di firma non è nella repo: la compilazione la prende dai segreti di GitHub.
- Funziona con la repo pubblica; dalla 0.0.7 gli aggiornamenti si installano senza disinstallare.

## Cedolino e diario di bordo
- Profilo dal cedolino: caricando un cedolino in PDF l'app legge profilo (macchinista o capotreno), livello, CID, data di assunzione, assegno ad personam e ferie residue, e imposta regole e parametri. Si può fare già alla prima apertura.
- Diario di bordo: importa il PDF mensile e legge ogni giornata (orari prescritti e reali, accessorie, stazioni, condotta o scorta, lavoro, notturno, straordinario, fuori residenza), anche i servizi a cavallo della mezzanotte.
- Diario contro agenda: orari, condotta o scorta, giornata di turno, riposi e assenze giorno per giorno; completa l'agenda con le giornate mancanti usando gli orari reali.
- Diario contro cedolino: presenze, riposi, ferie, malattia, ticket pasto e voci a ore dello stesso mese.

## Esportazione, importazione e calendario del telefono
- Esporta calendario (.ics): si sceglie cosa includere (servizi, riposi, ferie e permessi, altre assenze) e se partire da oggi; il file si salva dove vuoi, anche su Google Drive o nei Download del telefono.
- Importa calendario: rilegge un file .ics creato da Ferromicon (da Drive o dal telefono) e lo aggiunge all'agenda.
- Sincronizzazione col calendario del telefono con la stessa scelta di contenuto.

## Installazione
- Firma fissa dell'APK: dalla 0.0.7 gli aggiornamenti si installano sopra la versione precedente. La 0.0.6 usa una chiave di firma nuova: chi ha la 0.0.5 deve disinstallare e perde i dati locali.

## Calendario agente, CID ed esportazione
- Calendario da CID o cognome: legge il calendario degli agenti e i turni del personale mobile (PDF), ricostruisce il ciclo del turno e genera giornate con orari, condotta o scorta e treni, riposi, disponibilità e ferie.
- Ferie estive e invernali da riposo a riposo: dal primo riposo del periodo assegnato al riposo successivo.
- CID personale nelle Impostazioni e «Genera il mio calendario» con un tocco.
- Esportazione `.ics` anche su Google Drive; sincronizzazione con il calendario del telefono (eventi riconoscibili e rimovibili).
- I PDF importati restano in un file separato e si cancellano con un tocco.

## Il mio turno
- Profilo iniziale: all'avvio si scrive il proprio nome (che diventa il nome del profilo, mostrato nell'agenda) e si sceglie macchinista o capotreno.
- Turno dell'impianto: ciclo di giornate (preferiti e sigle R, RS, FE, D) che compila l'agenda fino al cambio turno, prolungabile al cambio orario successivo.
- Giornata di turno di default per ogni giorno, modificabile (treni in più o in meno), con ripristino e scelta di un'altra giornata.
- Controllo del ciclo (riposi e limiti, anche nel rientro dall'ultima alla prima giornata), numeri del ciclo, avviso di scadenza, condivisione del ciclo.
- Pianificazione ferie: periodi con più giorni consecutivi a casa.
- Registrazione rapida del ritardo all'arrivo.
- Copia di sicurezza prima di compilare o modificare molti giorni.

## Aspetto e navigazione
- Barra in basso (Agenda, Riepilogo, Strumenti, Impostazioni), schede a bordo verde, tema chiaro e scuro, widget.
- Registro degli errori con invio dei dettagli.

## Cedolini e netto
- Importazione di cedolini e CU in PDF, stima del netto di un'ora di straordinario, confronto con le competenze stimate.

## Regole e controlli
- Prestazione massima, riposi, condotta, notturno, refezione, riposo settimanale, limiti mensili e annui.
- Recupero festivi a 90 giorni, perimetri di residenza, riepilogo del mese e del giorno, dormite, profilo orario personalizzabile.
- Stima delle competenze variabili con i codici del ruolo paga.

## Agenda
- Calendario a griglia, righe treno con orari previsti e reali, preferiti, duplicazione, turni ciclici, importazione da testo, cestino, rubrica stazioni, orario in formato GTFS, backup, CSV.
