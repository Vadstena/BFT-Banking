# SEC-Project

## Account management

Stores information about cryptographic key pairs of the user accounts registered in the system.

Each file `<filename>-<key>.txt`, where `<filename>` corresponds to the hashed username, contains the corresponding encrypted `private` or `public` key, using AES, when `<key>` is `priv` or `pub`, respectively.