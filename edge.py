from flask import Flask, request, jsonify
import os
import pandas as pd
import numpy as np
import tensorflow.lite as tflite

TFLITE_MODEL_PATH = './model/big.tflite'  # SOSTITUISCI con il percorso del tuo modello .tflite
#CSV_FILE_PATH = './model/inputc.csv'          # SOSTITUISCI con il percorso del tuo file CSV
CSV_FILE_PATH = './uploads/input.csv'          # SOSTITUISCI con il percorso del tuo file CSV
TARGET_COLUMN_NAME = None 

if not os.path.exists(TFLITE_MODEL_PATH) or not os.path.exists(CSV_FILE_PATH):
    print("Uno o entrambi i file (modello TFLite/CSV) non trovati.")
    exit()

def load_tflite_model(model_path):
    """Carica il modello TFLite e alloca i tensori."""
    print(f"Caricamento del modello TFLite da: {model_path}...")
    try:
        interpreter = tflite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors() 
        print("Modello TFLite caricato e tensori allocati con successo.")
        return interpreter
    except Exception as e:
        print(f"Errore durante il caricamento del modello TFLite: {e}")
        return None

# --- 3. Funzione per Caricare e Preprocessare i Dati dal CSV (per TFLite) ---
def load_and_preprocess_csv_for_tflite(csv_path, target_column_name=None,
                                     expected_input_dtype=np.float32,
                                     expected_num_features=None):
    print(f"\nCaricamento dati da: {csv_path}...")
    try:
        df = pd.read_csv(csv_path)
    except FileNotFoundError:
        print(f"Errore: File CSV '{csv_path}' non trovato.")
        return None, None
    except Exception as e:
        print(f"Errore durante la lettura del CSV: {e}")
        return None, None

    X_df = df.copy() 
    y_actual_series = None # Etichette reali, se presenti

    if target_column_name and target_column_name in df.columns:
        print(f"Estrazione della colonna target: '{target_column_name}'")
        y_actual_series = X_df.pop(target_column_name) # Rimuove e restituisce la colonna target
    elif target_column_name:
        print(f"Attenzione: La colonna target '{target_column_name}' non è stata trovata nel CSV.")

   # Verifica del numero di feature
    if expected_num_features is not None and X_df.shape[1] != expected_num_features:
        print(f"ATTENZIONE: Il numero di feature nel CSV processato ({X_df.shape[1]}) "
              f"non corrisponde alle feature attese dal modello ({expected_num_features}).")
        print(f"Feature nel DataFrame: {X_df.columns.tolist()}")
        return None, None

    # Conversione finale in array NumPy e al tipo di dato corretto
    try:
        X_processed_all_rows = X_df.to_numpy().astype(expected_input_dtype)
    except Exception as e:
        print(f"Errore durante la conversione finale di X in NumPy array o nel casting del tipo: {e}")
        print("Controlla che tutte le colonne siano numeriche o siano state codificate correttamente.")
        return None, None

    print(f"\nDati CSV processati (X_processed_all_rows) pronti (shape: {X_processed_all_rows.shape}, dtype: {X_processed_all_rows.dtype}).")
    return X_processed_all_rows, y_actual_series

def inference():
    output = []
    # Carica il modello TFLite
    interpreter = load_tflite_model(TFLITE_MODEL_PATH)
    if interpreter is None:
        return

    # Ottieni i dettagli dei tensori di input e output
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("\n--- Dettagli Input Modello TFLite ---")
    input_index = input_details[0]['index']
    input_shape = input_details[0]['shape'] 
    input_dtype = input_details[0]['dtype']
    print(f"  Indice Input: {input_index}")
    print(f"  Shape Input Atteso: {input_shape}") # Il primo '1' è la dimensione del batch
    print(f"  Tipo Dati Input Atteso: {input_dtype}")

    print("\n--- Dettagli Output Modello TFLite ---")
    output_index = output_details[0]['index']
    output_shape = output_details[0]['shape']
    output_dtype = output_details[0]['dtype']
    # Parametri di dequantizzazione se l'output è quantizzato
    output_quant_params = output_details[0].get('quantization_parameters', None)
    output_scale = output_quant_params['scales'][0] if output_quant_params and len(output_quant_params.get('scales', [])) > 0 else 1.0
    output_zero_point = output_quant_params['zero_points'][0] if output_quant_params and len(output_quant_params.get('zero_points', [])) > 0 else 0

    print(f"  Indice Output: {output_index}")
    print(f"  Shape Output: {output_shape}")
    print(f"  Tipo Dati Output: {output_dtype}")
    if output_scale != 1.0 or output_zero_point != 0:
        print(f"  Output Scale: {output_scale}, Zero Point: {output_zero_point}")


    # Determina il numero di feature attese dal modello TFLite (escludendo la dimensione batch)
    expected_num_features_from_model = input_shape[-1] if len(input_shape) > 1 else None
    if len(input_shape) == 2: # Tipico per [batch_size, num_features]
        expected_num_features_from_model = input_shape[1]
    elif len(input_shape) > 2: # Es. per immagini [batch_size, H, W, C], il prodotto H*W*C
        expected_num_features_from_model = np.prod(input_shape[1:])

    all_input_data_np, all_actual_labels = load_and_preprocess_csv_for_tflite(
        CSV_FILE_PATH,
        TARGET_COLUMN_NAME,
        expected_input_dtype=input_dtype,
        expected_num_features=expected_num_features_from_model if len(input_shape) == 2 else None # Passalo solo se è un semplice array di feature
    )

    if all_input_data_np is None:
        print("Impossibile procedere con l'inferenza a causa di errori nel caricamento/preprocessing dei dati.")
        return

    print(f"\n--- Esecuzione dell'Inferenza ({all_input_data_np.shape[0]} campioni) ---")
    all_predictions = []

    for i in range(all_input_data_np.shape[0]):
        # Prendi una singola riga di dati (un campione)
        single_input_sample_np = all_input_data_np[i]

        if len(single_input_sample_np.shape) < len(input_shape):
            input_tensor_data = np.expand_dims(single_input_sample_np, axis=0)
        else:
            input_tensor_data = single_input_sample_np # Assumiamo che abbia già la forma corretta [1, ...]

        # Verifica che la forma finale corrisponda (tollerando None per dimensioni flessibili)
        if not all(s_model == s_data or s_model is None for s_model, s_data in zip(input_shape, input_tensor_data.shape)):
             print(f"ERRORE CRITICO: Shape del tensore di input preparato ({input_tensor_data.shape}) "
                   f"non corrisponde allo shape atteso dal modello ({input_shape}) per il campione {i}.")
             print("Controlla la logica di reshape e preprocessing.")
             continue # Salta questo campione


        # Imposta il tensore di input
        try:
            interpreter.set_tensor(input_index, input_tensor_data)
        except Exception as e:
            print(f"Errore durante set_tensor per il campione {i}: {e}")
            print(f"  Dati del tensore: shape={input_tensor_data.shape}, dtype={input_tensor_data.dtype}")
            print(f"  Dettagli input attesi: shape={input_shape}, dtype={input_dtype}")
            continue

        # Esegui l'inferenza
        interpreter.invoke()

        # Ottieni il tensore di output
        prediction_raw = interpreter.get_tensor(output_index)

        # (Opzionale) Dequantizza l'output se necessario
        if output_dtype == np.uint8 or output_dtype == np.int8:
            if output_scale != 1.0 or output_zero_point != 0:
                prediction_dequantized = output_scale * (prediction_raw.astype(np.float32) - output_zero_point)
                current_prediction = prediction_dequantized
            else: # Nessun parametro di dequantizzazione, usa raw
                current_prediction = prediction_raw
        else: # L'output è già float
            current_prediction = prediction_raw

        all_predictions.append(current_prediction)

        # Stampa l'output per il campione corrente
        actual_label_info = ""
        if all_actual_labels is not None and i < len(all_actual_labels):
            actual_label_info = f" (Etichetta Reale: {all_actual_labels.iloc[i]})"

        # Rimuovi la dimensione batch dall'output per la stampa, se presente
        if current_prediction.shape[0] == 1 and current_prediction.ndim > 1 :
            output_to_print = current_prediction[0]
        else:
            output_to_print = current_prediction


        if output_to_print.ndim == 0 or output_to_print.size == 1: # Regressione o classificazione binaria
            print(f"Campione {i+1}: Predetto = {output_to_print.item():.4f}{actual_label_info}")
        else: # Classificazione multi-classe (probabilità)
            predicted_class_idx = np.argmax(output_to_print)
            confidence = output_to_print[predicted_class_idx]
            print(f"Campione {i+1}: Predetto (raw) = {output_to_print}, Classe = {predicted_class_idx} (Conf: {confidence:.4f}){actual_label_info}")
            output.append(predicted_class_idx.item())

    print(f"\n--- Inferenza completata per {len(all_predictions)} campioni ---")
    print(output)
    ret = (max(set(output), key=output.count))
    print(ret)
    return ret

app = Flask(__name__)

# Configure the upload folder (create it if it doesn't exist)
UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

@app.route('/upload', methods=['POST'])
def upload_csv():
    """
    Handles CSV file uploads.
    Expects a file to be sent in the request body with the form field name 'file'.
    """
    if 'file' not in request.files:
        return jsonify({'error': 'No file part in the request'}), 400

    file = request.files['file']

    # If the user does not select a file, the browser submits an
    # empty file without a filename.
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    if file:
        print(request.form.get("model"))
        # Securely save the file
        filename = file.filename
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        try:
            file.save(filepath)
            result = inference()

            # Optional: Process the CSV file (e.g., read with pandas)
            # try:
            #     df = pd.read_csv(filepath)
            #     print(f"Successfully received and read CSV: {filename}")
            #     print(df.head()) # Print the first few rows
            #     # You can now process the data in the DataFrame
            # except Exception as e:
            #     print(f"Error reading CSV file: {e}")
            #     # You might want to return an error here if processing is mandatory

            return jsonify({'message': 'File uploaded successfully and inference completed.', 'result': result}), 200

        except Exception as e:
            return jsonify({'error': f'Error saving file: {e}'}), 500

    return jsonify({'error': 'An unexpected error occurred'}), 500

if __name__ == '__main__':
    # Run the Flask app
    # Setting debug=True provides helpful error messages during development
    # Setting host='0.0.0.0' makes the server accessible from other machines on the network
    # Setting port=5000 (or any other desired port)
    app.run(debug=True, host='0.0.0.0', port=8080)
