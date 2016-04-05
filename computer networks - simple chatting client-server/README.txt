Core functions of this program are described in the Programming Assignment 1.pdf file.

a. In the server program, there are 5 kinds of threads: talking to clients（Communicator), listening to participating clients on the port (Service), receiving messages from clients and make responses (Receiver), counting active time for online clients (ActiveTimer), and blocking IP addresses of a user who inputed wrong password (Blocker). When the server starts, 3 threads including 1 Communicator, 1 Service and 1 ActiveTimer will automatically start. Whenever a client initialize connection to the server, the server will start a new thread Receiver to communicate with the client.
The client program has 2 kinds of threads: initialize connection and receiving messages (Receiver), sending command and messages (Sender). When a client start, a Receiver will start, and if the user passes the authentication a Sender will start.

b. Before starting the server, input the port number. After the server has started, input server IP address and port number and then start the client. Follow the step of authentication to log in, and then the client can send commands to the server and get responses.

c. To broadcast message to all other users, just input "broadcast hello world", with 1 space separating "broadcast" and message.
To broadcast to some specific users, for example, just input "broadcast wikipedia apple hello world", with 1 space separating "broadcast", users and message.
To send messages to 1 user, input "message user hello world", with 1 space separating.
Whoelse and wholast commands will return "none" if no such users are found.
Other commands are the same as those in the Sample Run part of "Programming Assignments 1.pdf".

d. Extra features:
First, messages sent to offline users will be stored by the server, and will be sent to the users once they login.
Also, the server can also broadcast messages to all the users or some specific uses, similar to those client commands. Command "who" is used by the server to know all online users. Command "wholast" is used to know users who were online minutes ago. Command "broadcast" is used to send messages to all users. Command "message" is used to send message to a specific user. Command "exit" will terminate the server and thus logout all the users.