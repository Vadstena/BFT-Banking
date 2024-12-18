# SEC-Project

## Server backup files

Two types of file format:

- `<server_port>_bank.ser` corresponds to the most stable backup - always atomic and trusted
- `<server_port>_bank_tmp.ser` corresponds to the most recent backup - can be corrupted, therefore, not trusted