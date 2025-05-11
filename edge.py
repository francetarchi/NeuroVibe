from flask import Flask, request, jsonify
import os
import pandas as pd # Optional: for reading/processing the CSV

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
        # Securely save the file
        filename = file.filename
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        try:
            file.save(filepath)

            # Optional: Process the CSV file (e.g., read with pandas)
            # try:
            #     df = pd.read_csv(filepath)
            #     print(f"Successfully received and read CSV: {filename}")
            #     print(df.head()) # Print the first few rows
            #     # You can now process the data in the DataFrame
            # except Exception as e:
            #     print(f"Error reading CSV file: {e}")
            #     # You might want to return an error here if processing is mandatory

            return jsonify({'message': 'File uploaded successfully', 'filename': filename, 'filepath': filepath}), 200

        except Exception as e:
            return jsonify({'error': f'Error saving file: {e}'}), 500

    return jsonify({'error': 'An unexpected error occurred'}), 500

if __name__ == '__main__':
    # Run the Flask app
    # Setting debug=True provides helpful error messages during development
    # Setting host='0.0.0.0' makes the server accessible from other machines on the network
    # Setting port=5000 (or any other desired port)
    app.run(debug=True, host='0.0.0.0', port=8080)