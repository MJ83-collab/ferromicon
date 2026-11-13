# Novità

## 0.0.26 (versionCode 26)
- Aggiornamenti: all'apertura dell'app compare la notifica (e la finestra) se c'è una versione nuova; il permesso notifiche viene chiesto prima del controllo.
- Profili: test su PdC, PdA e Ufficio su turni giorno e notte.
- Figure professionali (CCNL 2025, art. 72) e assegnazione temporanea con data e nota nelle Impostazioni.
- Archivio: i nuovi campi figura e assegnazione vengono salvati e ripristinati.

## 0.0.25 (versionCode 25)
La 0.0.25 raccoglie anche le novità della 0.0.24, non pubblicata a parte.
- Impostazioni > Licenze open source: elenco di tutte le librerie incluse, aggiornato a ogni build (file LICENZE.md).
- Build: la compilazione precedente si ferma quando arriva una nuova modifica sulla stessa branch.
- Agente: il riepilogo del rotativo non si blocca più se il PDF non riporta date leggibili.
- Cedolini: nessun crash se il profilo non è impostato nelle Impostazioni.

## 0.0.24
- Recupero festività: avviso configurabile (attivo/disattivo, giorni prima della scadenza 1–30, predefinito 7).
- Copia di sicurezza in una cartella scelta; ripristino dopo aggiornamento con conferma.
- Primo avvio in tre passi: CID, nome e cognome, impianto e qualifica; l'app si adatta alla qualifica.
- Cerca CID: importa l'elenco dal telefono e conferma i tuoi dati.
- Cronologia: ricerca per data gg/mm; report PDF personalizzato per qualifica.
- Orari: ore inserite in formato HHmm; etichette accessibili sui pulsanti del mese.
- Corretti: duplicazione riga, dialogo di annullamento cancellazione, controlli al salvataggio.

Versione 0.0.24 (versionCode 24, mai pubblicata a parte: inclusa nella 0.0.25).

## 0.0.23
- Primo avvio: la pagina di benvenuto non tronca più la prima riga sotto la barra di stato.
- Schermata principale: menu laterale (☰) con tutte le voci della scheda Strumenti.
- Servizio: «Copia in altra data» dal menu a pressione lunga, scegliendo il giorno di arrivo.
- Periodi invernali: un'unica fonte per le date, la tabella di ogni anno. Senza tabella non compaiono date, anche nel calendario agente.

Versione 0.0.23 (versionCode 23).

## 0.0.22
- Aggiornamenti: il controllo di una versione nuova parte a ogni apertura dell'app, senza attendere le 12 ore.
- Primo avvio: si sceglie anche «Personale di ufficio»; la lettura del cedolino passa alla schermata successiva.

Versione 0.0.22 (versionCode 22).

## 0.0.21
- Pianificazione ferie: lettera del gruppo per ogni anno, con il ciclo D – A – C – E – F – B; periodo invernale con le date quando la tabella dell'anno è inserita.
- Calcoli interni del calendario dei riposi (sequenze A–G), verificati fino al 07/01/2028.

Versione 0.0.21 (versionCode 21).

## 0.0.20
- Benvenuto al primo avvio: funzioni dell'app, avviso legale (app non ufficiale, possibili errori), autore. Si rivede da Impostazioni › Informazioni e responsabilità.
- Home: saluto «Ciao, [nome]» quando il nome è impostato.
- Calendario: si scorre tra i mesi con il gesto o con le frecce; si può selezionare un giorno o un intervallo con il pulsante «Periodo», che mostra il riepilogo.
- Fine giornata: countdown alla fine prescritta; l'arrivo reale dell'ultimo treno si inserisce a mano e la fine reale somma gli accessori.
- Pianificazione ferie: inserimento di un periodo fisso con date gg/mm/aaaa; riposi e festivi del turno restano esclusi.
- Calcoli dello staff (ancora senza schermata dedicata): accessori di giornata = lavoro meno condotta, meno refezione e treni fuori servizio.

Versione 0.0.20 (versionCode 20).

## 0.0.19
- Profilo «Personale di ufficio» con regole proprie, separate dal capotreno: prestazione ordinaria 7 h 36', straordinario oltre, segnalazione del lavoro notturno.
- Flessibilità mensile dello staff: saldo con riporto di ±5 h, avviso quando lo supera.
- Cartellino SAP: controllo delle giornate con una sola timbratura (entrata e uscita; la pausa pranzo non si timbra) e delle uscite anticipate senza permesso.
- Permessi ad ore: calcolo come da vademecum (sotto l'ora si scala un'ora, sopra si scalano i minuti esatti, la giornata intera 7 h 36').
- Ex festività: quattro giorni individuali, conteggio dei giorni residui.

Versione 0.0.19 (versionCode 19). Ogni voce è una funzione aggiunta; il modulo `app` si controlla compilando su GitHub, il modulo `core` ha 105 test.

## 0.0.18
- Schermata principale: se il livello non è impostato compare un avviso che porta alle Impostazioni.

## 0.0.17
- Dopo un aggiornamento, il calendario del telefono viene risincronizzato da solo con i dati salvati (sul calendario già scelto in «Calendario del telefono»).
- Nessun livello predefinito: il livello parte come «Non impostato» e va scelto in Impostazioni (o letto dal cedolino). Senza livello la paga oraria è segnalata come incompleta.

## 0.0.16
- Privacy: dal repository pubblico sono stati tolti i dati personali rimasti nei test (nome, identificativo e indirizzo di una scheda SAP, area di lavoro). Nessuna modifica al comportamento dell'app.

## 0.0.15
- Report PDF: in Strumenti, «Report e dati». PDF di un mese o di un anno (si sceglie il mese o l'anno dal calendario), con agenda, assenze, cartellino SAP (flessibilità), cedolini (netto) e archivio (stazioni, perimetri, preferiti, CU). Il dettaglio giorno per giorno è facoltativo.
- Esportazione dei dati in CSV: uno zip con un file per tipo di dato (servizi, assenze, impostazioni, preferiti, stazioni, perimetri, cedolini, CU, cartellini SAP, turno). Ogni riga ha colonne leggibili e il dettaglio completo, si apre con Excel.
- Importazione dello zip CSV: aggiunge o sostituisce le voci con lo stesso identificativo.
- Cartellino SAP: riconosce «Dal … al …» e la voce «Flessibilià».

## 0.0.14
- Aggiornamenti: l'APK viene scaricato in una cartella visibile dal gestore file e poi si apre l'installazione di Android. Prima l'app usava la sessione dell'installatore, che con l'app aperta falliva con «deve essere chiuso durante l'aggiornamento».
- Se l'installazione non si apre, l'app dice dove si trova il file scaricato.

## 0.0.13
- Cartellino SAP: in Strumenti si sceglie il PDF stampato dal cartellino. L'app legge timbrature e flessibilità di ogni giorno e mostra il risultato; si salva solo dopo conferma. «Mostra il testo letto» serve a controllare la lettura.
- Profilo «Personale di ufficio»: si sceglie nelle Impostazioni. I widget mostrano il profilo scelto.
- Stabilità: un profilo sconosciuto in un servizio non fa più perdere l'archivio; l'avviso dei permessi e la home non possono più interrompere l'app se un dato non si legge.
- Ancora da fare: calcolo del cassetto (fondo cassa) e widget dedicati all'ufficio; profilo temporaneo/inidoneo.

## 0.0.12
- Wiki Ferrovia: in Strumenti. Tabelle dei tempi accessori, abbreviazioni, permessi e ricerca; funziona senza connessione.
- Permessi: si imposta quanti giorni spettano nell'anno (Impostazioni). La home mostra presi e restanti, e una notifica compare una volta al mese.
- Widget «Permessi da usare» (presi, restanti, avviso di fine anno).
- Widget «Prossimo riposo»: mostra il profilo scelto.
- Ancora da fare: profilo ufficio con i suoi widget; verifica timbrature (passo 2).

## 0.0.11
- Aggiornamenti: banner in home quando è disponibile una versione nuova, e notifica di sistema una sola volta per versione.
- Riposi: «Sposta questo riposo» dal menu del giorno, verso una data libera.
- Codici nel calendario e legenda: M malattia, I infortunio, F ferie, P permesso, PG giorno parentale, R riposo, RS riposo settimanale, RF recupero festivo, D disponibilità, A altra assenza.
- Straordinario: «Segna come straordinario» sul servizio del giorno; nel calendario compare S accanto all'orario.
- Parentali: riepilogo dei giorni presi e di quelli che restano; il numero spettante si imposta nelle Impostazioni.
- Calendario agente: i tre caricamenti sono in una scheda unica.
- Testi visibili: «Altarail» sostituito con «Azienda» (riferimenti normativi invariati).

## 0.0.10
- Aggiornamenti: ogni esito dell'installazione viene mostrato (conferma, successo, errore con codice). Se la conferma di Android non si apre, l'app lo dice.
- Aggiornamenti: il messaggio di errore del download indica il tipo di errore.

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
