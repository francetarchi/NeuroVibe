import os
import shutil

import pandas as pd
import numpy as np
import tensorflow.lite as tflite

from flask import Flask, request, jsonify


TARGET_COLUMN_NAME = None 

TFLITE_BIG_MODEL_PATH = "./edge/models/Bigmodel1000Neurons.tflite"
TFLITE_SMALL_MODEL_PATH = "./edge/models/Smallmodel100Neurons.tflite"

CSV_FILE_PATH = "./edge/uploads"


if not os.path.exists(TFLITE_BIG_MODEL_PATH):
    print("Big model non trovato.")
    exit()

if not os.path.exists(TFLITE_SMALL_MODEL_PATH):
    print("Small model non trovato.")
    exit()

if not os.path.exists(CSV_FILE_PATH):
    os.makedirs(CSV_FILE_PATH)
else:
    try:
        shutil.rmtree(CSV_FILE_PATH)
        os.makedirs(CSV_FILE_PATH)
    except Exception as e:
        print(f"Errore durante la pulizia della directory {CSV_FILE_PATH}: {e}")
        exit()


# --- Funzione per Caricare il Modello TFLite ---
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


# --- Funzione per Caricare e Preprocessare i Dati dal CSV (per TFLite) ---
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
    y_actual_series = None

    if target_column_name and target_column_name in df.columns:
        print(f"Estrazione della colonna target: '{target_column_name}'")
        y_actual_series = X_df.pop(target_column_name)
    elif target_column_name:
        print(f"Attenzione: La colonna target '{target_column_name}' non è stata trovata nel CSV.")

   # Verifico il numero di feature
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


# --- Funzione per Eseguire l'Inferenza ---
def inference(model_path, csv_path):
    output = []
    
    # Carico il modello TFLite
    interpreter = load_tflite_model(model_path)
    if interpreter is None:
        return

    # Ottengo i dettagli dei tensori di input e output
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("\n--- Dettagli Input Modello TFLite ---")
    input_index = input_details[0]['index']
    input_shape = input_details[0]['shape'] 
    input_dtype = input_details[0]['dtype']
    print(f"  Indice Input: {input_index}")
    print(f"  Shape Input Atteso: {input_shape}")
    print(f"  Tipo Dati Input Atteso: {input_dtype}")

    print("\n--- Dettagli Output Modello TFLite ---")
    output_index = output_details[0]['index']

    # Determino il numero di feature attese dal modello TFLite (escludendo la dimensione batch)
    expected_num_features_from_model = input_shape[-1] if len(input_shape) > 1 else None
    if len(input_shape) == 2:
        expected_num_features_from_model = input_shape[1]
    elif len(input_shape) > 2:
        expected_num_features_from_model = np.prod(input_shape[1:])

    all_input_data_np, all_actual_labels = load_and_preprocess_csv_for_tflite(
        csv_path,
        TARGET_COLUMN_NAME,
        expected_input_dtype=input_dtype,
        expected_num_features=expected_num_features_from_model if len(input_shape) == 2 else None
    )

    if all_input_data_np is None:
        print("Impossibile procedere con l'inferenza a causa di errori nel caricamento/preprocessing dei dati.")
        return

    print(f"\n--- Esecuzione dell'Inferenza ({all_input_data_np.shape[0]} campioni) ---")
    all_predictions = []
    first_row = all_input_data_np.shape[0] - 5
    last_row = all_input_data_np.shape[0]

    for i in range(first_row, last_row, 1):
        # Per ogni singola riga di dati (un chunk di 2 secondi)
        single_input_sample_np = all_input_data_np[i]

        if len(single_input_sample_np.shape) < len(input_shape):
            input_tensor_data = np.expand_dims(single_input_sample_np, axis=0)
        else:
            input_tensor_data = single_input_sample_np

        # Verifico che la forma finale corrisponda (tollerando None per dimensioni flessibili)
        if not all(s_model == s_data or s_model is None for s_model, s_data in zip(input_shape, input_tensor_data.shape)):
             print(f"ERRORE CRITICO: Shape del tensore di input preparato ({input_tensor_data.shape}) "
                   f"non corrisponde allo shape atteso dal modello ({input_shape}) per il campione {i}.")
             print("Controlla la logica di reshape e preprocessing.")
             continue

        # Imposto il tensore di input
        try:
            interpreter.set_tensor(input_index, input_tensor_data)
        except Exception as e:
            print(f"Errore durante set_tensor per il campione {i}: {e}")
            print(f"  Dati del tensore: shape={input_tensor_data.shape}, dtype={input_tensor_data.dtype}")
            print(f"  Dettagli input attesi: shape={input_shape}, dtype={input_dtype}")
            continue

        # Eseguo l'inferenza
        interpreter.invoke()

        # Ottengo il tensore di output
        current_prediction = interpreter.get_tensor(output_index)
        
        all_predictions.append(current_prediction)

        # Stampo l'output per il campione corrente
        actual_label_info = ""
        if all_actual_labels is not None and i < len(all_actual_labels):
            actual_label_info = f" (Etichetta Reale: {all_actual_labels.iloc[i]})"

        # Rimuovo la dimensione batch dall'output per la stampa, se presente
        if current_prediction.shape[0] == 1 and current_prediction.ndim > 1 :
            output_to_print = current_prediction[0]
        else:
            output_to_print = current_prediction

        if output_to_print.ndim == 0 or output_to_print.size == 1:
            print(f"Campione {i+1}: Predetto = {output_to_print.item():.4f}{actual_label_info}")
        else:
            predicted_class_idx = np.argmax(output_to_print)
            confidence = output_to_print[predicted_class_idx]
            print(f"Campione {i+1}: Predetto (raw) = {output_to_print}, Classe = {predicted_class_idx} (Conf: {confidence:.4f}){actual_label_info}")
            output.append(predicted_class_idx.item())

    print(f"\n--- Inferenza completata per {len(all_predictions)} campioni ---")
    print("--- INFO: Predicted classes for each data chunk: ", output)

    ret = (max(set(output), key=output.count))
    print("--- INFO: Final predicted class (mode): ", ret, "\n")

    return ret


# --- Inizializzazione dell'App Flask ---
app = Flask(__name__)


# --- Configurazione dell'App Flask ---
@app.route('/upload', methods=['POST'])
def upload_csv():
    """
    Handles CSV file uploads.
    Expects a file to be sent in the request body with the form field name 'file'.
    """
    print("\n\n\n\n")
    print("--- New HTTP request received ---")
    print("--- INFO: request IP address: ", request.remote_addr)

    if 'file' not in request.files:
        return jsonify({'error': 'No file part in the request'}), 400

    file = request.files['file']

    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    if file:
        model_to_use = request.form.get("model")
        print("--- INFO: requested model: ", model_to_use.upper(), "\n")

        filename = file.filename
        filepath = os.path.join(CSV_FILE_PATH, filename)
        try:
            file.save(filepath)

            selected_model_path = None
            if model_to_use == "small":
                selected_model_path = TFLITE_SMALL_MODEL_PATH
            elif model_to_use == "big":
                selected_model_path = TFLITE_BIG_MODEL_PATH
            else:
                return jsonify({'error': 'Invalid model type specified. Use "small" or "big".'}), 400
            
            if not selected_model_path or not os.path.exists(selected_model_path):
                return jsonify({'error': f'Model file not found for type: {model_to_use}'}), 500
            
            result = inference(selected_model_path, filepath)

            print("--- Sending HTTP response (to the IP address at the beginning of the next line) ---")
            return jsonify({'message': 'File uploaded successfully and inference completed.', 'result': result}), 200

        except Exception as e:
            return jsonify({'error': f'Error saving file: {e}'}), 500

    return jsonify({'error': 'An unexpected error occurred'}), 500


# --- Run the Flask app ---
if __name__ == '__main__':
    # Setting debug=True provides helpful error messages during development
    # Setting host='0.0.0.0' makes the server accessible from other machines on the network
    # Setting port=5000 (or any other desired port)
    app.run(debug=True, host='0.0.0.0', port=8080)
