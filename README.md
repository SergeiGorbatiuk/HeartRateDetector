# PulseDetector
### An android app for heart rate measurment using camera with flashlight
___
This application uses camera with flashlight to capture the image of a finger that is being held close to it, and then
extracts the red channel of RGB signal, to get a series of measurments, that is going to be interpreted like "cardiogram".
It then applies Fast Fourier Transformation to the time series to extract main frequency of the signal and it, after applying some methods directed to improve accuracy, is considered as an actual heart rate.
