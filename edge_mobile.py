import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
import numpy as np
# Prova a importare il runtime specifico di tflite, altrimenti usa tensorflow completo
try:
    import tflite_runtime.interpreter as tflite
    print("Utilizzo di tflite_runtime.interpreter")
except ImportError:
    import tensorflow as tf
    tflite = tf.lite # Usa tf.lite come alias per coerenza
    print("Utilizzo di tensorflow.lite.Interpreter")

#=========================================================================
#=========================================================================
# 1. Carica il dataset dal CSV
try:
    df = pd.read_csv('./model/input.csv')
except FileNotFoundError:
    print("Errore: File './model/input.csv' non trovato. Assicurati che sia nella directory corretta.")
    exit()

print("Prime 5 righe del DataFrame:")
print(df.head())

# 2. Separa le features (X) dalla colonna della classe (y)
# Assumiamo che la colonna della classe si chiami 'classe'
# e tutte le altre colonne siano features.

#try:
#    X = df.drop('classe', axis=1)  # 'axis=1' indica che stiamo eliminando una colonna
#    y_labels_original = df['classe']
#except KeyError:
#    print("Errore: La colonna 'classe' non è stata trovata nel CSV.")
#    print(f"Colonne disponibili: {df.columns.tolist()}")
#    exit()


# (Opzionale ma Spesso Necessario) Codifica delle Etichette (se le classi sono stringhe)
# Molti modelli richiedono che le etichette siano numeriche.
# Se le tue etichette sono già numeriche (es. 0, 1), puoi saltare questo passaggio.
#if y_labels_original.dtype == 'object' or pd.api.types.is_string_dtype(y_labels_original):
#    print("\nLe etichette sono stringhe, procedo con la Label Encoding...")
#    label_encoder = LabelEncoder()
#    y = label_encoder.fit_transform(y_labels_original)
#    print(f"Etichette originali: {y_labels_original.unique()}")
#    print(f"Etichette codificate: {pd.Series(y).unique()}")
#    # Puoi salvare le classi codificate per riferimento futuro:
#    # print(f"Mapping delle classi: {dict(zip(label_encoder.classes_, label_encoder.transform(label_encoder.classes_)))}")
#else:
#    y = y_labels_original
#    print("\nLe etichette sono già numeriche.")
#
## (Opzionale ma Consigliato) Conversione in array NumPy (spesso richiesto dai modelli)
X_np = df.to_numpy()
#y_np = y.to_numpy() # Se y è una Series pandas, altrimenti y è già un array numpy se hai usato LabelEncoder

print(f"\nShape delle features (X): {X_np.shape}")
#print(f"Shape delle etichette (y): {y_np.shape}")
print(f"\nPrime 5 righe di X (features):\n{X_np[:5]}")
#print(f"Prime 5 etichette y:\n{y_np[:5]}")

# (Opzionale ma Fondamentale per l'Addestramento) Divisione in set di addestramento e test
# X_train, X_test, y_train, y_test = train_test_split(X_np, y_np, test_size=0.2, random_state=42)
# print(f"\nShape X_train: {X_train.shape}, Shape y_train: {y_train.shape}")
# print(f"Shape X_test: {X_test.shape}, Shape y_test: {y_test.shape}")

# Ora X_np (o X_train) sono i tuoi dati di input e y_np (o y_train) sono le tue classi.
# Questi dati (es. X_np) andranno preprocessati ulteriormente per adattarsi
# allo shape e al dtype attesi dal tuo modello TFLite (come visto nell'esempio precedente).



#=========================================================================
#=========================================================================

# --- 1. Percorso del Modello ---
model_path = "./model/Smallmodel100Neurons.tflite"  # SOSTITUISCI CON IL PERCORSO REALE DEL TUO MODELLO

def run_tflite_model(model_path):
    # --- 2. Carica il Modello e Alloca i Tensori ---
    try:
        interpreter = tflite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()
        print("Modello TFLite caricato e tensori allocati con successo.")
    except Exception as e:
        print(f"Errore FATALE durante il caricamento del modello: {e}")
        return

    # --- 3. Ottieni Dettagli Input/Output ---
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("\n--- Dettagli Input ---")
    for i, detail in enumerate(input_details):
        print(f"Input {i}:")
        print(f"  Nome: {detail['name']}")
        print(f"  Shape: {detail['shape']}")
        print(f"  Tipo Dati: {detail['dtype']}")
        print(f"  Quantizzazione (parametri): {detail['quantization']}") # (scale, zero_point)

    print("\n--- Dettagli Output ---")
    for i, detail in enumerate(output_details):
        print(f"Output {i}:")
        print(f"  Nome: {detail['name']}")
        print(f"  Shape: {detail['shape']}")
        print(f"  Tipo Dati: {detail['dtype']}")
        print(f"  Quantizzazione (parametri): {detail['quantization']}")

    # Assumiamo un solo input e un solo output per questo esempio
    if not input_details or not output_details:
        print("Errore: Dettagli di input o output non trovati.")
        return

    input_shape = input_details[0]['shape']
    input_dtype = input_details[0]['dtype']
    # output_shape = output_details[0]['shape'] # Non usato direttamente qui sotto ma utile da sapere
    # output_dtype = output_details[0]['dtype'] # Non usato direttamente qui sotto ma utile da sapere

    # --- 4. Prepara i Dati di Input (Esempio Fittizio) ---
    # SOSTITUISCI QUESTA PARTE CON IL TUO REALE PREPROCESSING DEI DATI
    print("\n--- Preparazione Dati di Input (Esempio) ---")
    #try:
    #    # Se il modello è quantizzato (es. input_dtype è np.uint8 o np.int8)
    #    if input_dtype == np.uint8:
    #        # Crea dati di input uint8 nel range [0, 255]
    #        input_data = np.array(np.random.randint(0, 256, size=input_shape), dtype=np.uint8)
    #        print(f"Creati dati di input fittizi UINT8 con shape: {input_data.shape}")
    #    elif input_dtype == np.int8:
    #        # Crea dati di input int8 nel range [-128, 127]
    #        input_data = np.array(np.random.randint(-128, 128, size=input_shape), dtype=np.int8)
    #        print(f"Creati dati di input fittizi INT8 con shape: {input_data.shape}")
    #    elif input_dtype == np.float32:
    #        # Crea dati di input float32 (spesso normalizzati tra 0 e 1, o -1 e 1)
    #        input_data = np.array(np.random.random_sample(input_shape), dtype=np.float32)
    #        # Esempio se i dati dovessero essere normalizzati da un range 0-1 a 0-255 e poi scalati:
    #        # Questo dipende da come il modello è stato addestrato e convertito.
    #        # Per la quantizzazione aware training, l'input potrebbe essere float ma internamente il modello usa int.
    #        print(f"Creati dati di input fittizi FLOAT32 con shape: {input_data.shape}")
    #    else:
    #        # Tipo di dato non gestito specificamente in questo esempio
    #        input_data = np.array(np.random.random_sample(input_shape)).astype(input_dtype)
    #        print(f"Creati dati di input fittizi con dtype {input_dtype} e shape: {input_data.shape}")

    #    # Importante: Se il tuo modello è quantizzato e l'input_details['quantization']
    #    # ha scale e zero_point diversi da (0.0, 0), potresti dover quantizzare
    #    # i tuoi dati di input float secondo questi parametri se stai fornendo float a un modello che
    #    # internamente li converte. Ma di solito si forniscono dati già nel tipo intero atteso.
    #    # input_data_quantized = (input_data_float / scale) + zero_point
    #    # input_data = input_data_quantized.astype(input_dtype)

    #except Exception as e:
    #    print(f"Errore FATALE durante la preparazione dei dati di input: {e}")
    #    return

    # --- 5. Imposta il Tensore di Input ed Esegui l'Inferenza ---
    try:
        interpreter.set_tensor(input_details[0]['index'], input_data)
        print("\nTensore di input impostato.")
:x
        print("Esecuzione dell'inferenza...")
        interpreter.invoke()
        print("Inferenza completata.")
    except Exception as e:
        print(f"Errore FATALE durante l'impostazione dell'input o l'inferenza: {e}")
        return

    # --- 6. Ottieni i Risultati ---
    try:
        # Ottieni il tensore di output
        output_data = interpreter.get_tensor(output_details[0]['index'])
        print("\n--- Risultati dell'Output ---")
        print(f"Shape dell'output: {output_data.shape}")
        # print(f"Dati di output (primi elementi/righe):\n{output_data[:min(5, output_data.shape[0])]}")

        # Se l'output è quantizzato, potresti volerlo dequantizzare per interpretarlo come float
        output_quant_params = output_details[0]['quantization']
        if output_quant_params and output_quant_params[0] != 0.0: # (scale, zero_point)
            scale, zero_point = output_quant_params
            output_data_dequantized = scale * (output_data.astype(np.float32) - zero_point)
            print(f"Dati di output dequantizzati (primi elementi/righe):\n{output_data_dequantized[:min(5, output_data_dequantized.shape[0])]}")
            # Lavora con output_data_dequantized
            if output_data_dequantized.ndim == 2 and output_data_dequantized.shape[0] == 1: # tipico per classificazione
                predicted_class_index = np.argmax(output_data_dequantized[0])
                confidence = output_data_dequantized[0][predicted_class_index]
                print(f"  Indice della classe predetta (dequantizzato): {predicted_class_index} con confidenza: {confidence:.4f}")
            elif output_data_dequantized.ndim == 1:
                 predicted_class_index = np.argmax(output_data_dequantized)
                 confidence = output_data_dequantized[predicted_class_index]
                 print(f"  Indice della classe predetta (dequantizzato): {predicted_class_index} con confidenza: {confidence:.4f}")

        else:
            print(f"Dati di output (primi elementi/righe):\n{output_data[:min(5, output_data.shape[0])]}")
            # Lavora con output_data direttamente se non è quantizzato o la scala è 0
            if output_data.ndim == 2 and output_data.shape[0] == 1: # tipico per classificazione
                predicted_class_index = np.argmax(output_data[0])
                confidence = output_data[0][predicted_class_index]
                print(f"  Indice della classe predetta: {predicted_class_index} con confidenza: {confidence:.4f}")
            elif output_data.ndim == 1:
                predicted_class_index = np.argmax(output_data)
                confidence = output_data[predicted_class_index]
                print(f"  Indice della classe predetta: {predicted_class_index} con confidenza: {confidence:.4f}")


    except Exception as e:
        print(f"Errore FATALE durante l'ottenimento dell'output: {e}")
        return

if __name__ == '__main__':
    # Crea un finto file .tflite per testare lo script se non ne hai uno
    # Questo crea un modello semplicissimo (input float32[1,1] -> output float32[1,1] che raddoppia l'input)
    # NON USARE QUESTO MODELLO FITTIZIO PER ALTRO CHE TESTARE LO SCRIPT DI CARICAMENTO
    try:
        # Controlla se il file modello esiste, altrimenti creane uno fittizio
        import os
        if not os.path.exists(model_path):
            print(f"File modello '{model_path}' non trovato. Creazione di un modello fittizio per test...")
            # Definisci un semplice modello Keras
            simple_model = tf.keras.Sequential([
                tf.keras.layers.InputLayer(input_shape=[1]), # Input scalare
                tf.keras.layers.Dense(units=1, kernel_initializer=tf.keras.initializers.Constant(value=2), use_bias=False) # Output = 2 * Input
            ])
            # Converti in TFLite
            converter = tf.lite.TFLiteConverter.from_keras_model(simple_model)
            tflite_model_content = converter.convert()
            with open(model_path, 'wb') as f:
                f.write(tflite_model_content)
            print(f"Modello fittizio '{model_path}' creato.")
            print("--- Esecuzione con modello fittizio ---")
            run_tflite_model(model_path)
            print("\n--- Esempio di esecuzione con modello fittizio completato ---")
            print("--- RICORDA di sostituire 'il_tuo_modello.tflite' con il tuo vero modello! ---")
        else:
            print(f"Utilizzo del modello esistente: '{model_path}'")
            run_tflite_model(model_path)

    except NameError: # Se tensorflow non è installato per creare il modello fittizio
         print(f"Il file modello '{model_path}' non esiste.")
         print("Per testare questo script senza un modello reale, installa 'tensorflow' completo per creare un modello fittizio.")
    except Exception as e:
        print(f"Errore durante la creazione del modello fittizio o l'esecuzione: {e}")
