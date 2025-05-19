# NeuroVibe
Project for Mobile and Social Sensing System course.

L'applicazione permette di ricevere dati da un casco per EEG, raccolti mentre l'utente (dall'applicazione stessa) guarda opere d'arte.
L'applicazione fornisce successivamente i dati raccolti in input ad un modello TensorFlow Lite che predice se l'utente ha gradito o meno il dipinto.
I modelli inclusi nell'app sono due:
- uno "SMALL" da 100 neuroni
- uno "BIG" da 1000 neuroni
Inoltre, ognuno dei due modelli è eseguibile in locale o in remoto con un approccio edge server.



## Mobile app
Nella directory "./NeuroVibe" si trova tutto il necessario per l'applicazione Android.
Per eseguirla:
- aprire Android Studio alla cartella root "NeuroVibe"
- connettere il telefono Android
- fare un "Run" per installare l'applicazione sul telefono
- disconnettere il telefono dal computer se si vuole verificare i consumi dell'applicazione senza che il telefono venga caricato
- eseguire l'applicazione sul telefono.

### Connessione al MindRove
L'applicazione funziona con il MindRove Arc.
Il casco, all'accensione, genera la rete Wi-Fi "MindRove_ARC_ae01ec", con un server DHCP integrato che assegna gli indirizzi IP automaticamente in ordine di connessione:
- il casco stesso è raggiungibile all'indirizzo "192.168.4.1"
- il primo dispositivo che si connette è raggiungibile all'indirizzo "192.168.4.2"
- il secondo dispositivo che si connette è raggiungibile all'indirizzo "192.168.4.3"

Il casco invia dati solamente al dispositivo connesso da più tempo.
Per evitare errori (mancata ricezione di dati dal dispositivo ), è CONSIGLIATO:
- accendere il MindRove
- connettere per primo alla rete WiFi del MindRove il telefono Android con cui vogliamo eseguire la raccolta dei dati EEG.
- connettere per secondo alla rete WiFi del MindRove il computer su cui verrà eseguito il server edge per l'esecuzione dei modelli da remoto.

In ogni caso, l'applicazione Android, quando deve inviare dati all'edge server, richiede l'indirzzo IP a cui inviarli: controllare l'indirizzo IP dell'edge server dalle proprietà della rete WiFi a cui si è connessi.

#### PASSWORD ####
La password per connettersi al MindRove è "#mindrove".

#### ATTENZIONE ####
Dopo diversi test, è possibile che il MindRove invii sempre gli stessi dati non invii più nulla. Per risolvere, semplicemente spegnere e riacccendere il MindRove.



## Edge server
L'edge server è un file Python (al path "./edge/edge.py") che esegue un server Flask incaricato di attendere costantemente richieste HTTP da qualsiasi indirizzo IP: alla ricezione di una richiesta HTTP, elabora i dati ricevuti in un file.csv utilizzando il modello indicato nella stessa e successivamente invia la classe predetta all'indirizzo IP da cui ha ricevuto la richiesta.
I dati in input arrivano divisi in chunk (l'applicazione registra dati EEG per 10 secondi, suddividendoli in 5 chunk da 2 secondi l'uno).
La classe predetta è la moda delle predizioni sui singoli chunk.

Ulteriori informazioni riguardo il funzionamento dell'edge server si trovano al path "./edge/README.md", in quanto il server è utilizzabile copiando il contenuto della sola directory "./edge".
