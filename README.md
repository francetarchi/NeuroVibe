# NeuroVibe
Project for the course of Mobile and Social Sensing Systems of the Master Degree in Artificial Intelligence and Data Engineering, University of Pisa.

<p align="center">
  <img src="NeuroVibe/app/src/main/res/drawable-night/logo_neurovibe.png" alt="logo NeuroVibe" width="200">
</p>

The application allows receiving data from an EEG headset, collected while the user (through the application itself) looks at works of art.
The application then provides the collected data as input to a TensorFlow Lite model that predicts whether the user liked the painting or not.

The models included in the app are two:
- one "SMALL" with 100 neurons;
- one "BIG" with 1000 neurons.

In addition, each of the two models can be executed locally or remotely with an edge server approach.



## Mobile app
In the directory "./NeuroVibe" you can find everything needed for the Android application.
To run it:
- open Android Studio at the root folder "NeuroVibe";
- connect the Android phone;
- click "Run" to install the application on the phone;
- disconnect the phone from the computer if you want to check the application’s power consumption without charging the phone;
- run the application on the phone.

### Connection to MindRove
The application works with the MindRove Arc.
When powered on, the headset generates the Wi-Fi network "MindRove_ARC_ae01ec", with an integrated DHCP server that automatically assigns IP addresses in connection order:
- the headset itself is reachable at the address "192.168.4.1"
- the first device that connects is reachable at the address "192.168.4.2"
- the second device that connects is reachable at the address "192.168.4.3"

The headset only sends data to the device that has been connected the longest.
To avoid errors (failure to receive data on the device), it is RECOMMENDED to:
- power on the MindRove;
- connect first to the MindRove WiFi network with the Android phone that will be used for EEG data collection;
- connect second to the MindRove WiFi network with the computer where the edge server will run for remote model execution.

In any case, the Android application, when it needs to send data to the edge server, requests the IP address to which it should send them: check the IP address of the edge server from the WiFi network properties.

#### PASSWORD ####
The password to connect to the MindRove is "#mindrove".

#### WARNING ####
After several tests, it may happen that the MindRove always sends the same data or no longer sends anything. To solve this, simply power off and on the MindRove.



## Edge server
The edge server is a Python file (at path "./edge/edge.py") that runs a Flask server constantly waiting for HTTP requests from any IP address: upon receiving an HTTP request, it processes the received data in a .csv file using the model specified in the request and then sends the predicted class to the IP address from which the request was received.

The input data arrives divided into chunks (the application records EEG data for 10 seconds, splitting them into 5 chunks of 2 seconds each).
The predicted class is the mode of the predictions on the individual chunks.

Further information regarding the operation of the edge server can be found at path "./edge/README.md", since the server can be used by copying only the contents of the "./edge" directory.
