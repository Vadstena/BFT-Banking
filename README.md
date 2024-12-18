Instituto Superior Técnico

Master's Degree in Computer Science and Engineering

Highly Dependable Systems 2021/2022

# Byzantine Fault Tolerant Banking

The goal of this project is to develop a Byzantine Fault Tolerant Banking, known as BFT
Banking.

The system is divided into client and server. The client operates through the client API, an
underlying library accountable for receiving the required parameters and sending requests to
the server.

The BFT Banking system maintains a set of bank accounts, each account being uniquely
identified by a public key pair. Each account stores:
- the current balance
- the history of incoming and outgoing operations for that account

## Security

- Authenticity: every user message containing requested data and the user’s public key is signed with the user’s private key. This is also done on the server side, where every server response is signed with the server’s private key. (Because users do not store server public keys in memory, they request them to validate signatures.)

- Integrity: Signatures are done on top of a message digest and sent to the server along with the original message. The server then computes the hash of the original message and decyphers the signature with the public key. Ultimately, both digests ought to match. Otherwise, the server detects the message was tampered with (**man-in-the-middle attack**).

- Nonces: prior to an operation, the client requests a new nonce, which is sent afterward, alongside the operation request. The server only accepts requests associated with
the last generated nonce and, after completing the operation, that saved nonce is deleted. Thereby thwarting any **replay attacks**, including ones utilizing recent nonces.

## Dependability

- Server Atomic Persistence.

- Client Key Pairs Persistence.

- Anti-Spam Mechanisms similar to **Proof of Work** that prevent **Denial of Service Attacks**.

- Protect against **Byzantine Servers** by using (N, N) byzantine atomic registers.

More details on the aforementioned implementation features can be found in the reports in [docs](docs/).


# Authors
**Group 34**

79730 João Silva

92513 Mafalda Ferreira

92451 Diogo Barbosa

# Technology used

- Java programming language
- gRPC: framework of remote procedure calls that supports client and server communication
- Maven: build automation tool for Java projects
- Protobuf: cross-platform data used to serialize structured data
- JUnit: unit testing framework for Java programming language

# Requirements

- Java Developer Kit 12 (JDK 12)
- Maven 3

To confirm all versions are correctly installed:

    javac -version
    mvn -version

# Compile and Install

To compile and install all modules:

    cd bftbanking
    mvn clean install -DskipTests

# Run JUnit Tests

Compile and install all modules, as seen above.

Server receives as parameters its identifier `<id>`, number of servers `<N>` and number of faults `<f>`, which are by default id=0, N=3, f=1 if no arguments are provided through the command line.

We recommend to run N=3 servers and f=1 to perform JUnit tests.

After setting up **all servers** and before running the tests, you need to press `ENTER` on each server terminal to fetch the public keys.

Start server 0

    cd bftbanking/server
    mvn exec:java -Dexec.args="0 3 1"

Start server 1

    cd bftbanking/server
    mvn exec:java -Dexec.args="1 3 1"

Start server 2

    cd bftbanking/server
    mvn exec:java -Dexec.args="2 3 1"

Run tests

    cd bftbanking
    mvn test

# Demo Walkthrough

Compile and install all modules, as seen above.

Make sure to **delete all backup files** at `bftbanking/server/backups/` **before starting the servers**, in order to **delete any previous bank state**.

Server receives as parameters its identifier `<id>`, number of servers `<N>` and number of faults `<f>`, which are by default id=0, N=4, f=1 if no arguments are provided through the command line.

The system will have three servers running on ports 8000, 8001, 8002 and 8003. The system shall tolerate f=1 servers faults.

After setting up *all servers* and before running the demo, you need to press ENTER on each server terminal to fetch the public keys.

Start server `0` on port 8000

    cd bftbanking/server
    mvn exec:java -Dexec.args="0 4 1"

Start server `1` on port 8001

    cd bftbanking/server
    mvn exec:java -Dexec.args="1 4 1"

Start server `2` on port 8002

    cd bftbanking/server
    mvn exec:java -Dexec.args="2 4 1"

Start server `3` on port 8003

    cd bftbanking/server
    mvn exec:java -Dexec.args="3 4 1"

Start client `0` considering 4 servers and 1 server fault

    cd bftbanking/client
    mvn exec:java -Dexec.args="4 1"

Start client `1` considering 4 servers and 1 server fault

    cd bftbanking/client
    mvn exec:java -Dexec.args="4 1"


**Press** `ENTER` **on each server terminal to fetch the public keys.**

The following demo is divided into multiple chapters, named as `SERVER|CLIENT X:`, where `X` corresponds to the server or client terminal with **id=X**.

## CLIENT 0: User `0`: Register to generate key pairs

    >>> ClientMain <<<

    Please select one option:
    1. Login
    2. Register
    0. Exit
    > 2

    =REGISTER= 
    Please insert username:
    > user0

    Please insert password:
    > pw0

    Registered successfully!

## CLIENT 0: User `0`: Open Account

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 1
    Created a new bank account with ID = 0

## CLIENT 1: User `1`: Register to generate key pairs

    >>> ClientMain <<<

    Please select one option:
    1. Login
    2. Register
    0. Exit
    > 2

    =REGISTER= 
    Please insert username:
    > user1

    Please insert password:
    > pw1

    Registered successfully!

## CLIENT 1: User `1`: Open Account

Create a bank account associated with the public key.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 1
    Created a new bank account with ID = 1

Try to create another bank account associated with the same public key.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 1
    ALREADY_EXISTS: Account associated with given public key already exists.

## CLIENT 0: User `0`: Send Amount to User `1`

Create a valid transaction of 10 euros to user 1.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > 10
    Are you sure you create a transfer of 10 euros to user ID = 1 ? (Y/N):
    > Y
    Created a pending transfer with TID = 0

Create a valid transaction of 25 euros to user 1.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > 25
    Are you sure you create a transfer of 25 euros to user ID = 1 ? (Y/N):
    > Y
    Created a pending transfer with TID = 1

Try to send amount to a non existent bank account.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > -10
    Are you sure you create a transfer of 10 euros to user ID = 1 ? (Y/N):
    > Y
    INVALID_ARGUMENT: Invalid amount. Must be higher than zero.

Try to send amount to itself.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 0
    Enter a valid amount or type 'exit' to return:
    > 5
    Are you sure you create a transfer of 5 euros to user ID = 0 ? (Y/N):
    > Y
    FAILED_PRECONDITION: Destination account can't be the same as the source account.

Try to create a transaction with an invalid amount.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > -10
    Are you sure you create a transfer of -10 euros to user ID = 1 ? (Y/N):
    > Y
    INVALID_ARGUMENT: Invalid amount. Must be higher than zero.

Finally, try to create a transaction with insufficient funds.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > 10000
    Are you sure you create a transfer of 10000 euros to user ID = 1 ? (Y/N):
    > Y
    FAILED_PRECONDITION: Insufficient balance.

## CLIENT 1: User `1`: Check Account

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 3

    [checkAccount] Balance: 50.
    Pending Transfers:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 10

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 25

(Note that hash codes of public keys, presented in transactions, might differ from this demo.)
    
## CLIENT 1: User `1`: Receive Amount

Receive amount from a valid transaction

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 4

    Enter a valid transaction ID or type 'exit' to return:
    > 0

    Are you sure you want to complete transfer ID 0 ? (Y/N):
    > Y
    Received amount of 10 euros

Try to receive amount from an invalid transaction

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 4

    Enter a valid transaction ID or type 'exit' to return:
    > 9

    Are you sure you want to complete transfer ID 9 ? (Y/N):
    > Y
    NOT_FOUND: Transaction associated with TID does not exist.

## CLIENT 1: User `1`: Check Account

Check account after completing a transaction of 10 euros.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 3

    [checkAccount] Balance: 60.
    Pending Transfers:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 25

## CLIENT 1: User `1`: Audit

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 5

    [audit] Transaction history:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 10

## CLIENT 0: User `0`: Audit

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 5

    [audit] Transaction history:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 10


# Dependability Testing

Now we are going to focus on dependability of the server.

## SERVER 3: Take server `3` down

We start by taking server with **ID=3** down using **CTRL+C**.

## CLIENT 0: User `0`: Send Amount

Send amount of `1` from client `0` to client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > 1
    Are you sure you create a transfer of 1 euros to user ID = 1 ? (Y/N):
    > Y
    Created a pending transfer with TID = 2

## CLIENT 0: User `0`: Send Amount

Send amount of `2` from client `0` to client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit-
    6. Exit
    > 2
    Enter a valid account ID or type 'exit' to return:
    > 1
    Enter a valid amount or type 'exit' to return:
    > 2
    Are you sure you create a transfer of 2 euros to user ID = 1 ? (Y/N):
    > Y
    Created a pending transfer with TID = 3

## CLIENT 1: User `1`: Check Account

Check account of client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 3
    [checkAccount] Balance: 60.
    Pending Transfers:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 25

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 1

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 2

## SERVER 3: Restart server `3`

Restart server with **ID=3**

    mvn exec:java -Dexec.args="3 4 1"

Press **ENTER** to fetch public keys.

## CLIENT 1: User `1`: Check Account

Check account of client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 3
    [checkAccount] Balance: 60.
    Pending Transfers:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 25

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 1

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 2

## CLIENT 1: User `1`: Receive Amount

Receive amount from client `0` of `2` euros.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 4
    Enter a valid transaction ID or type 'exit' to return:
    > 3
    Are you sure you want to complete transfer ID 3 ? (Y/N):
    > Y
    Received amount of 2 euros.

## CLIENT 1: User `1`: Audit

Audit client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 5
    
    [audit] Transaction history:
    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 10

    Source (hashed): -2048921629 ---> Destination (hashed): -2131281945, amount: 2

## SERVER 3: Take server `3` down

We start by taking server with **ID=3** down using **CTRL+C**.

## SERVER 3: Take server `2` down

We start by taking server with **ID=2** down using **CTRL+C**.

## CLIENT 1: User `1`: Audit

Audit client `1`.

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    6. Exit
    > 5
    UNAVAILABLE: ERROR: insufficient server quorum. Got 2 out of 4 servers.

## CLIENT 0: User `0`: Exit

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 0
    Exiting client...

## CLIENT 1: User `1`: Exit

    Please select one option:
    1. Open Account
    2. Send Amount
    3. Check Account
    4. Receive Amount
    5. Audit
    0. Exit
    > 0
    Exiting client...
