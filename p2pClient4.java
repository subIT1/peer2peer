package verteiltesysteme.socket.p2p;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class p2pClient4 {
    public static ArrayList<String> clients = new ArrayList<>();
    private static ArrayList<String> neighbours = new ArrayList<>();
    public static ArrayList<Socket> neighborSockets = new ArrayList<>();
    public static ArrayList<String> messageIP = new ArrayList<>();
    public static ArrayList<p2pSearchObjectBerkley> searchObjects = new ArrayList<>();
    public static HashMap<Integer, String> searchWithMessageTransfer = new HashMap<>();
    public static HashMap<String, String> fileTags = new HashMap<>();
    public static ArrayList<String> berkleyList = new ArrayList<>();
    public static ArrayList<String> actualFiles = new ArrayList<>();
    public static long berkleyStart;
    public static long berkleyEnd;

    private static int ID;
    private static int IDCounter = 1;
    private static boolean FLAG_Message = false;
    private static String message;
    private static boolean FLAG_Search = false;
    private static boolean FLAG_SyncStart = false;
    private static boolean FLAG_Networking = false;

    public static final int TIME_TO_LIVE = 8;
    public static int electionCounter = 0;
    public static int highestElectID = -1;
    public static String FLAG_State = "";
    private static String tmpReceived = "";
    public static boolean FLAG_Election_started = false;
    public static boolean FLAG_Berkley_started = false;
    public static String LeaderMessage = "";
    private static Timer timer = new Timer();
    private static Timer BerkleyTimer = new Timer();
    private static Timer networkingTimer = new Timer();
    private static boolean FLAG_Networking_Timer = false;

    private static String hostname;
    private static TimerTask timerTask;
    private static TimerTask networkingTimerTask;
    private static LocalTime systemTime;

    /**
     * Parameters to change
     */
    public static final int TCP_PORT = 13337; //Port to change
    public static String path = "/PATH/FILE.dat"; //path to leader entry file

    public static Thread t2 = new Thread() {
        public void run() {
            try {
                peerTCPServer(hostname, TCP_PORT);
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
                DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                Date date = new Date();
                System.out.println("[" + dateFormat.format(date) + "]  The address" + hostname + ":" + TCP_PORT + " could not establish a connection!");
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    };

    public static void main(String[] argv) throws Exception {
        showCredits();

        hostname = InetAddress.getLocalHost().getHostAddress();
        System.out.println(hostname);
        connecting(hostname, TCP_PORT);

        //BerkleyTimer
        Thread t1 = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        systemTime = systemTime.plusSeconds(1);
                        Thread.sleep(998);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        t1.start();

        Thread t4 = new Thread() {
            @Override
            public void run() {
                try {
                    sendRequest(hostname, TCP_PORT);
                } catch (Exception e) {
                    System.out.println("ERROR: " + e.getMessage());
                    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    Date date = new Date();
                    System.out.println("[" + dateFormat.format(date) + "] The address " + hostname + ":" + TCP_PORT + "could not establish a connection! n");
                    System.out.println("ERROR: " + e.getMessage());
                }
            }
        };


        t2.start();
        t4.start();

        Thread newNeighbor = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < neighborSockets.size(); i++) {
                    if (!neighborSockets.get(i).isClosed()) {
                        if (neighborSockets.size() < 1) {
                            printMessage("INFO: new neighbour requested!");
                            connecting(hostname, TCP_PORT);
                        }
                    }
                }
            }
        };
        newNeighbor.start();
    }

    public synchronized static void berkley(int ttl) throws IOException {
        berkleyStart = systemTime.toNanoOfDay();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        sendBerkleyNeighbor(hostname, TCP_PORT, ttl, null, systemTime.format(dtf));
        if (!FLAG_Berkley_started) {
            FLAG_Berkley_started = true;

            BerkleyTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    berkleyEnd = systemTime.toNanoOfDay();
                    //long timeIsGone = berkleyEnd - berkleyStart;
                    LocalTime difference = LocalTime.parse("00:00:00.000000");

                    for (String s : berkleyList) {
                        LocalTime time = LocalTime.parse(s.split("-")[1]);
                        difference = difference.plusHours(time.getHour());
                        difference = difference.plusMinutes(time.getMinute());
                        difference = difference.plusSeconds(time.getSecond());
                    }

                    LocalTime avg = LocalTime.parse(difference.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    int hour = avg.getHour() / (berkleyList.size() + 1);
                    int minute = avg.getMinute() / (berkleyList.size() + 1);
                    int second = avg.getSecond() / (berkleyList.size() + 1);
                    avg = LocalTime.parse((hour <= 9 ? "0" + hour : hour) + ":"
                            + (minute <= 9 ? "0" + minute : minute) + ":" +
                            (second <= 9 ? "0" + second : second));
                    avg = avg.minusHours(7);

                    systemTime = systemTime.plusHours(avg.getHour());
                    systemTime = systemTime.plusMinutes(avg.getMinute());
                    systemTime = systemTime.plusSeconds(avg.getSecond());

                    for (String s : berkleyList) {
                        try {
                            Socket socket = new Socket(s.split("-")[0].split(":")[0], Integer.parseInt(s.split("-")[0].split(":")[1]));
                            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                            LocalTime deviation = LocalTime.parse(s.split("-")[1]);
                            LocalTime initial = LocalTime.parse("00:00:00");
                            //24:00:00 - Deviation that was mentioned
                            LocalTime secondDifference = initial.minusSeconds(deviation.getSecond());
                            LocalTime minuteDifference = secondDifference.minusMinutes(deviation.getMinute());
                            LocalTime hoursDifference = minuteDifference.minusHours(deviation.getHour());
                            //Special case when time is running out
                            if (Boolean.parseBoolean(s.split("-")[2])) {
                                LocalTime secondMinus = hoursDifference.plusSeconds(2 * deviation.getSecond());
                                LocalTime minuteMinus = secondMinus.plusMinutes(2 * deviation.getMinute());
                                hoursDifference = minuteMinus.plusHours(2 * deviation.getHour());
                            }

                            //Add up Avg Time
                            LocalTime secondPlus = hoursDifference.plusSeconds(avg.getSecond());
                            LocalTime minutePlus = secondPlus.plusMinutes((avg.getMinute()));
                            LocalTime hourPlus = minutePlus.plusHours(avg.getHour());
                            dos.writeBytes("152-" + hourPlus.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n");
                            dos.flush();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    printMessage("--------------------");
                    printMessage("Local time is now " + systemTime + "!");
                    printMessage("(change by " + avg + " Time units)");
                    printMessage("--------------------");

                    berkleyList.clear();
                    FLAG_Berkley_started = false;
                }
            }, 5000);
        }
    }

    public static synchronized void sendBerkleyNeighbor(String hostname, int port, int ttl, Socket clientSocket, String systemTime) throws IOException {
        if (ttl > 0) {
            for (Socket neighborSocket : neighborSockets) {
                if (!neighborSocket.isClosed()) {
                    if (clientSocket == null || (!neighborSocket.getInetAddress().equals(clientSocket.getInetAddress()) && !(neighborSocket.getPort() == (clientSocket.getPort())))) {
                        DataOutputStream outToServer = new DataOutputStream(neighborSocket.getOutputStream());
                        outToServer.writeBytes("150-" + hostname + ":" + port + "-" + systemTime + "-" + ttl + '\n');
                        outToServer.flush();
                    } else {
                        if (!neighborSocket.getInetAddress().equals(clientSocket.getInetAddress()) && neighborSocket.getPort() != clientSocket.getPort()) {
                            DataOutputStream outToServer = new DataOutputStream(neighborSocket.getOutputStream());
                            outToServer.writeBytes("150-" + neighborSocket.getInetAddress() + ":" + neighborSocket.getPort() + "-" + systemTime + "-" + ttl + '\n');
                            outToServer.flush();
                        }

                    }
                }
            }
        }
    }

    /**
     * Rendering a console output with TimeStamp
     *
     * @param msg Message to be logged
     */
    public synchronized static void printMessage(String msg) {
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("[" + dateFormat.format(date.getTime()) + "] " + msg);
    }

    /**
     * Execute the Connect and enter the neighbors!
     *
     * @param hostname the client hostname
     * @param tcpPort  the client port
     */
    public synchronized static void connecting(String hostname, int tcpPort) {
        systemTime = LocalTime.now();
        printMessage("Read file");

        try {
            ArrayList<String> leaderList = peerReadFile();

            if (leaderList.size() == 0 || leaderList.get(0).equals("") || leaderList.get(0).equals(" ")) {
                String leading = "" + hostname + ":" + tcpPort + "";
                BufferedWriter writer = new BufferedWriter(new FileWriter(path));
                writer.write(leading);
                writer.newLine();
                writer.close();
                printMessage("a new leader has been appointed! (" + leading + ")");
                clients.add(hostname + ":" + tcpPort);
                ID = 0;
            } else {

                printMessage("File was read out.");

                String leaderlessHostname = leaderList.get(0).split(":")[0];
                int leaderListPort = Integer.parseInt(leaderList.get(0).split(":")[1]);

                for (int i = 0; i < leaderList.size(); i++) {
                    printMessage("Client inquired: " + leaderList.get(i));
                    if (!leaderList.get(i).split(":")[0].equals(hostname) || Integer.parseInt(leaderList.get(i).split(":")[1]) != tcpPort) {
                        try {
                            Socket clientSocket = new Socket(leaderlessHostname, leaderListPort);
                            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            outToServer.writeBytes("01-" + hostname + ":" + tcpPort + '\n');
                            outToServer.flush();
                            String modifiedSentence = new BufferedReader(new InputStreamReader(new DataInputStream(clientSocket.getInputStream()))).readLine();
                            printMessage("FROM SERVER: " + modifiedSentence);

                            if (modifiedSentence.startsWith("02-")) {
                                StringBuilder msg = new StringBuilder("INFO: Your neighbors are: ");
                                String[] s = modifiedSentence.split("-");
                                ID = Integer.parseInt(s[1]);
                                System.out.println("ID: " + ID);
                                clientSocket.close();

                                for (int j = 2; j < s.length; j++) {
                                    FLAG_State = "Connecting";
                                    if (!s[j].startsWith("0.0.0.0:")) {
                                        boolean isAlreadyListed = false;
                                        for (int k = 0; k < neighbours.size(); k++) {
                                            if (s[k].equals(neighbours.get(k))) {
                                                isAlreadyListed = true;
                                                break;
                                            }
                                        }

                                        if (!isAlreadyListed) {
                                            neighbours.add(s[j]);
                                            msg.append(s[j]).append(", ");
                                            try {
                                                Socket socket = new Socket(s[j].split(":")[0], Integer.parseInt(s[j].split(":")[1]));
                                                neighborSockets.add(socket);

                                                DataOutputStream outToNeighbor = new DataOutputStream(socket.getOutputStream());
                                                DataInputStream inFromNeighbour = new DataInputStream(socket.getInputStream());
                                                outToNeighbor.writeBytes("200\n");
                                                outToNeighbor.flush();

                                                String res = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream()))).readLine();
                                                if (res.length() > 0) {
                                                    System.out.println(res);
                                                }

                                                Thread t1 = new Thread(() -> listenToServerRequest(socket, outToNeighbor, inFromNeighbour, tcpPort, hostname));
                                                printMessage("Connected and added!");
                                                t1.start();
                                            } catch (Exception e) {
                                                printMessage(e.getMessage());
                                            }
                                        }
                                    }
                                }
                                printMessage(msg.toString());
                            }
                        } catch (IOException e) {
                            printMessage("Timeout reached!!! for the client " + leaderList.get(i));
                            removeFromFile(leaderList.get(i));
                            leaderList.remove(i);
                        }
                    } else {
                        printMessage("You are already a leader");
                        clients.add(hostname + ":" + tcpPort);
                        ID = 0;
                    }
                }

                //set yourself as a leader
                if (leaderList.size() == 0) {

                    setLeaderToList(hostname, tcpPort);
                    String actLeader = peerReadFile().get(0);
                    if (actLeader.equals(hostname + ":" + tcpPort)) {
                        ID = 0;
                    } else {
                        ID = -1;
                    }
                }
            }
            try {
                Thread.sleep(1000);
                if (ID > 0 && neighbours.size() == 0) {
                    connecting(hostname, tcpPort);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            printMessage("The file contains no contents!");
        }
        if (ID > 0 && neighbours.size() == 0) {
            connecting(hostname, tcpPort);
        }
    }

    /**
     * Writes this client as leader in the list
     *
     * @param hostname the client hostname
     * @param tcpPort  the client Port
     */
    public synchronized static void setLeaderToList(String hostname, int tcpPort) {
        String actualLeader = "";
        try {
            if (!(hostname + ":" + tcpPort).equals(actualLeader)) {
                String leading = hostname + ":" + tcpPort;
                BufferedWriter writer = new BufferedWriter(new FileWriter(path));
                writer.write(leading);
                writer.newLine();
                writer.close();
                printMessage("a new leader has been appointed! (" + leading + ")");
                clients.add(hostname + ":" + tcpPort);
            }
        } catch (Exception e) {
            printMessage("Leader could not be set!");
        }
    }

    /**
     * TCP server of the client listens for messages and connections.
     * Please note, if a connection is opened, it will continue to be kept active in the thread until a client acknowledges
     *
     * @param hostname the Client hostname
     * @param tcpPort  the Client port
     */
    public static void peerTCPServer(String hostname, int tcpPort) {
        FLAG_State = "Server";
        try {
            @SuppressWarnings("resource")
            ServerSocket welcomeSocket = new ServerSocket(tcpPort);
            printMessage("TCP Server started. Waiting for incoming requests...");

            while (true) {
                Socket connectionSocket = welcomeSocket.accept();

                Thread t1 = new Thread(() -> {
                    try {
                        electionCounter = 0;
                        String clientSentence;
                        StringBuilder capitalizedSentence;
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

                        while (!connectionSocket.isClosed()) {
                            try {
                                clientSentence = inFromClient.readLine();
                                if (clientSentence != null) {
                                    if (clientSentence.startsWith("01")) {
                                        String IPAddress = clientSentence.split("-")[1].split(":")[0];
                                        String clientPort = clientSentence.split("-")[1].split(":")[1];

                                        //01-127.128.52.36:3213-4
                                        try {
                                            ArrayList<String> peers = peerReadFile();
                                            capitalizedSentence = new StringBuilder("02-" + IDCounter++ + "-");
                                            if (clients.size() > 2) {
                                                Collections.shuffle(clients);
                                            }
                                            for (int i = 0; i < clients.size() && i < 2; i++) {
                                                capitalizedSentence.append(clients.get(i)).append("-");
                                            }
                                            int notServedYet = 2 - clients.size();
                                            for (int i = notServedYet; i > 0; i--) {
                                                if (i == 1) {
                                                    capitalizedSentence.append("0.0.0.0:" + TCP_PORT);
                                                } else {
                                                    capitalizedSentence.append("0.0.0.0:" + TCP_PORT + "-");
                                                }
                                            }

                                            outToClient.writeBytes(capitalizedSentence + "\n");
                                            outToClient.flush();
                                            connectionSocket.close();
                                            if (peers.get(0).equals(hostname + ":" + tcpPort)) {
                                                if (!clients.contains(IPAddress + ":" + clientPort)) {
                                                    clients.add(IPAddress + ":" + clientPort);
                                                }
                                            }
                                        } catch (Exception e) {
                                            printMessage("The file contains no contents!");
                                        }

                                    } else if (clientSentence.startsWith("03")) {
                                        String lineToRemove = clientSentence.split("-")[1];
                                        peerExit(lineToRemove, hostname, tcpPort);
                                        connectionSocket.close();
                                    } else if (clientSentence.startsWith("04")) {
                                        peerSearch(clientSentence, hostname, tcpPort, connectionSocket); //.split("-")[1]
                                    } else if (clientSentence.startsWith("05")) {
                                        printMessage("Reply received from: " + clientSentence.split("-")[1].split(":")[0]);

                                        if (FLAG_Message) {
                                            FLAG_Message = false;
                                            outToClient.writeBytes(message + "\n");
                                            outToClient.flush();
                                        } else if (FLAG_Search) {
                                            FLAG_Search = false;
                                            printMessage("The ID you are looking for: " + clientSentence.split("-")[2] + " has the IP address: " + clientSentence.split("-")[1]);
                                            //sendFile1(connectionSocket, searchWithMessageTransfer.get(Integer.parseInt(clientSentence.split("-")[2])));
                                            connectionSocket.close();
                                        } else if (FLAG_Networking) {
                                            printMessage("The ID you are looking for: " + clientSentence.split("-")[2] + " has the IP address: " + clientSentence.split("-")[1]);
                                            if (peerReadFile().get(0).equals(hostname + ":" + TCP_PORT)) {
                                                clients.add(clientSentence.split("-")[1]);
                                            }
                                            outToClient.writeBytes("Thank you, for your message!");
                                            outToClient.flush();

                                            if (!FLAG_Networking_Timer) {
                                                networkingTimer.schedule(networkingTimerTask = new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        FLAG_Networking_Timer = false;
                                                        FLAG_Networking = false;
                                                        System.out.println("TimerTask stopped");
                                                    }
                                                }, 5000);
                                            } else {
                                                timerTask.cancel();
                                                networkingTimer.cancel();
                                                FLAG_Networking = true;
                                                FLAG_Networking_Timer = true;
                                                networkingTimer.schedule(networkingTimerTask = new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        FLAG_Networking_Timer = false;
                                                        FLAG_Networking = false;
                                                        System.out.println("TimerTask stopped!");
                                                    }
                                                }, 5000);
                                            }
                                            connectionSocket.close();
                                        }
                                    } else if (clientSentence.startsWith("06")) {
                                        checkElection(clientSentence, connectionSocket, hostname, tcpPort);

                                    } else if (clientSentence.startsWith("07")) {
                                        increaseElectionCounter(connectionSocket, clientSentence);
                                    } else if (clientSentence.startsWith("08")) {
                                        receivedLeaderElectionMessage(connectionSocket, clientSentence);
                                    } else if (clientSentence.startsWith("102-")) {
                                        //102-Filename-Content
                                        printMessage("Received file: " + clientSentence.split("-")[1]);
                                        System.out.println(clientSentence);
                                        receiveFile(clientSentence);
                                    } else if (clientSentence.startsWith("100-")) {
                                        //100-filename-hostname-system time
                                        clientFileMessage(connectionSocket, clientSentence);
                                    } else if (clientSentence.startsWith("101-")) {
                                        //101.hostname:Port-filename-systemtime
                                    } else if (clientSentence.startsWith("150-")) {
                                        berkleyFlagCheck(connectionSocket, clientSentence);
                                    } else if (clientSentence.startsWith("151-")) {
                                        berkleyList.add(clientSentence.split("-")[1] + "-" + clientSentence.split("-")[2] + "-" + clientSentence.split("-")[3]);
                                    } else if (clientSentence.startsWith("152-")) {
                                        calculateExtraTime(clientSentence);
                                    } else {
                                        boolean connectionStayOpen = false;
                                        boolean neighborStatus = false;
                                        for (int n = 0; n < neighborSockets.size(); n++) {
                                            if (neighborSockets.get(n).isClosed()) {
                                                neighbours.remove(n);
                                                neighborSockets.remove(n);
                                            }

                                            if (neighborSockets.get(n).getInetAddress().equals(connectionSocket.getInetAddress()) && neighborSockets.get(n).getPort() == connectionSocket.getPort()) {
                                                neighborStatus = true;


                                                connectionStayOpen = true;
                                            }
                                        }
                                        if (!neighborStatus && neighborSockets.size() < 4) {
                                            neighbours.add(connectionSocket.getInetAddress().toString().substring(1) + ":" + connectionSocket.getPort());
                                            neighborSockets.add(connectionSocket);
                                            connectionStayOpen = true;
                                        }

                                        printMessage("Message from Client(" + connectionSocket.getInetAddress() + ") received: " + clientSentence);

                                        String capitalizedSentence1 = clientSentence.toUpperCase() + '\n';
                                        try {
                                            checkDuplicatedSearings(connectionSocket, outToClient, capitalizedSentence1);

                                            if (!connectionStayOpen) {
                                                connectionSocket.close();
                                            }
                                        } catch (IOException e) {
                                            printMessage("Message could not be sent!");
                                        }

                                    }
                                } else throw new IOException();
                            } catch (IOException io) {
                                peerConnectionClosed(connectionSocket, hostname, tcpPort);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        printMessage("The address " + connectionSocket.getInetAddress() + ":" + connectionSocket.getPort() + " could not establish a connection.");
                    }
                });
                t1.start();
            }
        } catch (Exception e) {
            System.out.println("Exception triggered! " + e.getMessage());
        }
    }

    private static void calculateExtraTime(String clientSentence) {
        LocalTime extraTime = LocalTime.parse(clientSentence.split("-")[1]);
        systemTime = systemTime.plusHours(extraTime.getHour());
        systemTime = systemTime.plusMinutes(extraTime.getMinute());
        systemTime = systemTime.plusSeconds(extraTime.getSecond());

        printMessage("--------------------");
        printMessage("Local time is now " + systemTime + "!");
        printMessage("(change by: " + extraTime + " Time units)");
        printMessage("--------------------");

        FLAG_SyncStart = false;
    }

    private static void berkleyFlagCheck(Socket connectionSocket, String clientSentence) throws IOException {
        if (!FLAG_SyncStart) {
            FLAG_SyncStart = true;
            //150-IP:Port-Systime-TTL
            sendBerkleyAnswer(clientSentence.split("-")[1].split(":")[0], Integer.parseInt(clientSentence.split("-")[1].split(":")[1]), LocalTime.parse(clientSentence.split("-")[2]));
            sendBerkleyNeighbor(clientSentence.split("-")[1].split(":")[0], Integer.parseInt(clientSentence.split("-")[1].split(":")[1]),
                    Integer.parseInt(clientSentence.split("-")[3]) - 1, connectionSocket, clientSentence.split("-")[2]);
        }
    }

    private static void clientFileMessage(Socket connectionSocket, String clientSentence) {
        String tmp = "The Client: " + clientSentence.split("-")[2] + " has the file: " + clientSentence.split("-")[1];
        if (!tmp.equals(tmpReceived)) {
            printMessage(tmp);
            sendFileInformation(clientSentence.split("-")[1], clientSentence.split("-")[2], Integer.parseInt(clientSentence.split("-")[3]) - 1, connectionSocket);
            tmpReceived = tmp;
        }
    }

    private static void increaseElectionCounter(Socket connectionSocket, String clientSentence) {
        if (Integer.parseInt(clientSentence.split("-")[1]) > ID) {
            electionCounter++;
        }
        if (Integer.parseInt(clientSentence.split("-")[1]) > highestElectID) {
            highestElectID = Integer.parseInt(clientSentence.split("-")[1]);
            sendHighestIDToNeighbours(highestElectID, connectionSocket);
        }
    }

    /**
     * send the answer of the berkly algorithm
     *
     * @param hostname   the local hostname
     * @param port       the local port
     * @param systemTime the local system time
     * @throws IOException by not founding the file
     */
    private static void sendBerkleyAnswer(String hostname, int port, LocalTime systemTime) throws IOException {
        Socket socket = new Socket(hostname, port);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        LocalTime hourDifference;
        boolean isMinus = false;
        LocalTime secondDifference = systemTime.minusSeconds(p2pClient4.systemTime.getSecond());
        LocalTime minuteDifference = secondDifference.minusMinutes(p2pClient4.systemTime.getMinute());
        hourDifference = minuteDifference.minusHours(p2pClient4.systemTime.getHour());
        LocalTime initial = LocalTime.parse("00:00:00");
        //24:00:00 - deviation that was mentioned.
        LocalTime secondDifference1 = initial.minusSeconds(hourDifference.getSecond());
        LocalTime minuteDifference1 = secondDifference1.minusMinutes(hourDifference.getMinute());
        LocalTime hoursDifference = minuteDifference1.minusHours(hourDifference.getHour());

        if (p2pClient4.systemTime.isAfter(systemTime)) {
            isMinus = true;
            dos.writeBytes("151-" + p2pClient4.hostname + ":" + p2pClient4.TCP_PORT + "-" + hourDifference + "-" + isMinus + "\n");
        } else {
            dos.writeBytes("151-" + p2pClient4.hostname + ":" + p2pClient4.TCP_PORT + "-" + hoursDifference + "-" + isMinus + "\n");
        }
    }

    /**
     * exit a peer of the net
     *
     * @param lineToRemove the peer IP:Port message to remove in the file
     * @param hostname     the local hostname
     * @param tcpPort      the local port
     */
    private synchronized static void peerExit(String lineToRemove, String hostname, int tcpPort) {
        try {
            for (int k = 0; k < neighbours.size(); k++) {
                if (neighbours.get(k).equals(lineToRemove)) {
                    neighbours.remove(k);
                    neighborSockets.get(k).close();
                    neighborSockets.remove(k);
                }
            }

            String leader = peerReadFile().get(0);
            if (lineToRemove.equals(leader.split(":")[0])) {
                sendlectionRequest(hostname, tcpPort);
                for (int k = 0; k < clients.size(); k++) {
                    if (clients.get(k).equals(lineToRemove)) {
                        clients.remove(k);
                    }
                }
            }

            printMessage(lineToRemove + " has left the network");
        } catch (Exception e) {
            printMessage("The registered leader is unknown.");
        }
    }


    /**
     * Second socket endpoint (Server --> Client). Here the client listens for messages from the server. is required, otherwise no bidirectional communication can take place.
     *
     * @param connectionSocket the connection socket
     * @param outToClient      the DataOutputStream
     * @param inFromClient     optional the DataInputStream
     * @param tcpPort          the Peer-TCP-Port
     * @param hostname         the Peer-TCP-Hostname
     */
    private static void listenToServerRequest(Socket connectionSocket, DataOutputStream outToClient, DataInputStream inFromClient, int tcpPort, String hostname) {
        try {
            printMessage("Listener for Peer (" + connectionSocket.getInetAddress() + ":" + connectionSocket.getPort() + ") opened");
            String clientSentence;

            while (!connectionSocket.isClosed()) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(connectionSocket.getInputStream())));
                    clientSentence = br.readLine();
                    if (clientSentence != null) {
                        if (clientSentence.startsWith("03")) {
                            //peerExit(clientSentence.split("-")[1], outToClient, hostname, tcpPort);
                            //connectionSocket.close();
                        } else if (clientSentence.startsWith("04")) {
                            peerSearch(clientSentence, hostname, tcpPort, connectionSocket);
                        } else if (clientSentence.startsWith("05")) {
                            printMessage("Reply received from: " + clientSentence.split("-")[1].split(":")[0]);

                            if (FLAG_Message) {
                                FLAG_Message = false;
                                outToClient.writeBytes(message + "\n");
                                outToClient.flush();
                            } else if (FLAG_Search) {
                                FLAG_Search = false;
                                printMessage("The ID you are looking for: " + clientSentence.split("-")[2] + " has the IP address: " + clientSentence.split("-")[1]);
                                connectionSocket.close();
                            }
                        } else if (clientSentence.startsWith("06")) {
                            checkElection(clientSentence, connectionSocket, hostname, tcpPort);
                        } else if (clientSentence.startsWith("07")) {
                            increaseElectionCounter(connectionSocket, clientSentence);
                        } else if (clientSentence.startsWith("08")) {
                            receivedLeaderElectionMessage(connectionSocket, clientSentence);
                        } else if (clientSentence.startsWith("102-")) {
                            //102-Filename-Content
                            printMessage("Received file: " + clientSentence.split("-")[1]);
                            receiveFile(clientSentence);
                        } else if (clientSentence.startsWith("100-")) {
                            //100-filename-hostname
                            clientFileMessage(connectionSocket, clientSentence);
                        } else if (clientSentence.startsWith("101-")) {
                            //101.hostname:Port-filename
                        } else if (clientSentence.startsWith("150-")) {
                            berkleyFlagCheck(connectionSocket, clientSentence);
                        } else if (clientSentence.startsWith("151")) {
                            berkleyList.add(clientSentence.split("-")[1] + "-" + clientSentence.split("-")[2] + "-" + clientSentence.split("-")[3]);
                        } else if (clientSentence.startsWith("152-")) {
                            calculateExtraTime(clientSentence);
                        } else {
                            printMessage("Message from Client(" + connectionSocket.getInetAddress() + ") received: \n\t" + clientSentence);

                            String capitalizedSentence1 = clientSentence.toUpperCase() + '\n';
                            try {
                                checkDuplicatedSearings(connectionSocket, outToClient, capitalizedSentence1);
                            } catch (IOException e) {
                                printMessage("Message could not be sent!");
                            }
                        }
                    } else throw new IOException();
                } catch (IOException io) {
                    if (!FLAG_State.equalsIgnoreCase("Connecting")) {
                        peerConnectionClosed(connectionSocket, hostname, tcpPort);
                        break;
                    } else {
                        for (int i = 0; i < neighborSockets.size(); i++) {
                            if (neighborSockets.get(i).getInetAddress().equals(connectionSocket.getInetAddress()) && neighborSockets.get(i).getPort() == connectionSocket.getPort()) {
                                connectionSocket.close();
                                neighborSockets.remove(i);
                                neighbours.remove(i);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR in listenToServerRequest(): " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void checkDuplicatedSearings(Socket connectionSocket, DataOutputStream outToClient, String capitalizedSentence1) throws IOException {
        boolean wasSent = false;
        for (int i = 0; i < messageIP.size(); i++) {
            if (messageIP.get(i).equals(connectionSocket.getInetAddress() + ":" + connectionSocket.getPort())) {
                wasSent = true;
                messageIP.remove(i);
            }
        }
        if (!wasSent) {
            outToClient.writeBytes(capitalizedSentence1);
            outToClient.flush();
        }
    }

    /**
     * Method that is called, as soon as an 08 message arrives
     *
     * @param connectionSocket the connection socket
     * @param clientSentence   the message of the election
     */
    private static synchronized void receivedLeaderElectionMessage(Socket connectionSocket, String clientSentence) {
        if (!LeaderMessage.equals(clientSentence.split("-")[1])) {
            if (FLAG_Election_started) {
                FLAG_Election_started = false;
                timerTask.cancel();
                timer.cancel();
                timer.purge();
                timer = new Timer();
            }
            electionCounter = 0;
            highestElectID = -1;
            FLAG_Election_started = false;
            LeaderMessage = clientSentence.split("-")[1];
            printMessage("--------------------");
            printMessage("The new leader is now " + LeaderMessage + "!");
            printMessage("--------------------");
            sendElectionFinishedInformation(clientSentence.split("-")[1].split(":")[0], Integer.parseInt(clientSentence.split("-")[1].split(":")[1]),
                    Integer.parseInt(clientSentence.split("-")[2]), connectionSocket.getInetAddress().toString(), connectionSocket.getPort());
        }
    }

    /**
     * sends the message 100- (file updated) to the neighbours, with socket by the sender from whom we received the message is not informed again.
     *
     * @param filename the filename
     * @param hostname the host name defined by IP:Port
     * @param ttl      the time to live, so that the packages do not circulate forever
     * @param socket   the socket where the info came in!
     */
    private synchronized static void sendFileInformation(String filename, String hostname, int ttl, Socket socket) {
        if (ttl > 0) {
            for (Socket neighbour : neighborSockets) {
                if (socket != null) {
                    if (!(neighbour.getInetAddress().equals(socket.getInetAddress()) && neighbour.getPort() == socket.getPort())) {
                        try {
                            DataOutputStream dos = new DataOutputStream(neighbour.getOutputStream());
                            dos.writeBytes("100-" + filename + "-" + hostname + "-" + ttl + "\n");
                            dos.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        DataOutputStream dos = new DataOutputStream(neighbour.getOutputStream());
                        dos.writeBytes("100-" + filename + "-" + hostname + "-" + ttl + "\n");
                        dos.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * send highest ID to neighbours
     *
     * @param id               the highest ID
     * @param connectionSocket the connection socket
     */
    private synchronized static void sendHighestIDToNeighbours(int id, Socket connectionSocket) {
        try {
            for (Socket s : neighborSockets) {
                if (!s.getInetAddress().equals(connectionSocket.getInetAddress())) {
                    if (!s.isClosed()) {
                        DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                        dos.writeBytes("07-" + id + "\n");
                        dos.flush();
                    }
                }
            }
        } catch (IOException io) {
            printMessage("No neighbours available!");
        }
    }

    /**
     * check the election by preventing issues with the election
     *
     * @param clientSentence   the message
     * @param connectionSocket the socket of the connection
     * @param hostname         the client hostname
     * @param tcpPort          the client port
     * @throws Exception by not founding the file
     */
    private synchronized static void checkElection(String clientSentence, Socket connectionSocket, String hostname, int tcpPort) throws Exception {
        int electionID = Integer.parseInt(clientSentence.split("-")[2]);
        if (electionID > highestElectID) {
            if (ID > electionID) {
                send07toClientSentenceIP(clientSentence, ID);
                //send new Election Message to your neighbors with your ID
                for (int i = 0; i < neighborSockets.size(); i++) {
                    sendlectionRequest(hostname, tcpPort);
                }
            } else {
                if (Integer.parseInt(clientSentence.split("-")[3]) > 0) { //TTL
                    for (Socket neighborSocket : neighborSockets) {
                        if (!connectionSocket.getInetAddress().equals(neighborSocket.getInetAddress())) {
                            //forward message to all neighbours, except the already requested client!
                            Socket ns = neighborSocket;
                            DataOutputStream dos = new DataOutputStream(ns.getOutputStream());
                            String param1 = clientSentence.split("-")[1];
                            String param2 = clientSentence.split("-")[2];
                            int param3 = Integer.parseInt(clientSentence.split("-")[3]);
                            --param3;

                            dos.writeBytes("06-" + param1 + "-" + param2 + "-" + param3 + "\n");
                            dos.flush();
                        }
                    }
                }
            }
        } else {
            send07toClientSentenceIP(clientSentence, highestElectID);
        }
    }

    /**
     * send a return message of the election to the message ip
     *
     * @param clientSentence the message to send
     * @param highestElectID the temporary highest ID which was found
     * @throws IOException by not founding the file
     */
    private synchronized static void send07toClientSentenceIP(String clientSentence, int highestElectID) throws IOException {
        Socket socket = new Socket(clientSentence.split("-")[1].split(":")[0], Integer.parseInt(clientSentence.split("-")[1].split(":")[1]));
        DataOutputStream outToElectionTrigger = new DataOutputStream(socket.getOutputStream());
        outToElectionTrigger.writeBytes("07-" + highestElectID + "\n");
        outToElectionTrigger.flush();
        socket.close();
    }

    /**
     * check weather a client is disconnected or not
     *
     * @param socket   the socket of the peer
     * @param hostname the hostname of the peer
     * @param port     the port of the peer
     * @throws Exception by having issues with the neighbors
     */
    private synchronized static void peerConnectionClosed(Socket socket, String hostname, int port) throws Exception {
        boolean isLeader = false;
        try {
            String leader = peerReadFile().get(0);
            for (int i = 0; i < neighborSockets.size(); i++) {
                if (socket.getInetAddress().equals(neighborSockets.get(i).getInetAddress()) && socket.getPort() == neighborSockets.get(i).getPort()) {
                    neighborSockets.get(i).close();
                    String oldNeighbor = neighbours.get(i);
                    neighborSockets.remove(i);
                    neighbours.remove(i);

                    if (leader.split(":")[0].equals(oldNeighbor.split(":")[0])) {
                        sendlectionRequest(hostname, port);
                        isLeader = true;
                    }
                    if (!isLeader) {
                        Socket leaderSocket = new Socket(leader.split(":")[0], Integer.parseInt(leader.split(":")[1]));
                        DataOutputStream dos = new DataOutputStream(leaderSocket.getOutputStream());
                        dos.writeBytes("03-" + socket.getInetAddress() + ":" + socket.getPort() + "\n");
                        leaderSocket.close();
                    }
                }
            }

        } catch (Exception e) {
            //printMessage("The registered leader is unknown.");
        }
    }

    /**
     * peerSearch method checks if he himself is the client you are looking for, if he is not, he sends the request to his neighbours
     *
     * @param clientSentence   the incoming message
     * @param hostname         the IP address of this client
     * @param tcpPort          the TCP port of this client
     * @param connectionSocket the ConnectionSocket on which the request came in
     * @throws IOException by reading the file
     */
    public synchronized static void peerSearch(String clientSentence, String hostname, int tcpPort, Socket connectionSocket) throws IOException {
        String requesterIp = clientSentence.split("-")[2].split(":")[0];
        int requesterPort = Integer.parseInt(clientSentence.split("-")[2].split(":")[1]);
        int id = Integer.parseInt(clientSentence.split("-")[1]);
        if (Integer.parseInt(clientSentence.split("-")[1]) == ID) {
            printMessage("I was wanted!");
            try {
                Socket socket = new Socket(clientSentence.split("-")[2].split(":")[0], Integer.parseInt(clientSentence.split("-")[2].split(":")[1]));
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeBytes("05-" + hostname + ":" + tcpPort + "-" + ID + "\n");
                dos.flush();
                BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream())));
                try {
                    String msg = br.readLine();
                    printMessage("Message from Client(" + socket.getInetAddress() + ") received (search): \n\t" + msg);
                } catch (Exception e) {
                    //printMessage("The client has disconnected.");
                }
            } catch (Exception e) {
                printMessage("The Client " + clientSentence.split("-")[2].split(":")[0] + ":" + Integer.parseInt(clientSentence.split("-")[2].split(":")[1]) + " has left the net!");
            }

        } else {
            int TTL = Integer.parseInt(clientSentence.split("-")[3]) - 1;
            if (TTL > 0) {
                sendSearchMessageToNeighbours(id, requesterIp, requesterPort, TTL, connectionSocket);
            }
        }
    }

    /**
     * remove a specified text out of the file
     *
     * @param host the text that has to be removed
     * @throws Exception by not founding the file
     */
    public synchronized static void removeFromFile(String host) throws Exception {
        printMessage("Entferne Client: " + host);

        File file = new File(path);
        List<String> outList = Files.lines(file.toPath())
                .filter(line -> !line.contains(host))
                .collect(Collectors.toList());
        Files.write(file.toPath(), outList, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * read the leader out of the file path
     *
     * @return the entries in the file
     * @throws Exception by not founding the file
     */
    public synchronized static ArrayList<String> peerReadFile() throws Exception {
        ArrayList<String> leaderList = new ArrayList<>();
        File f = new File(path);
        if (!f.exists()) {
            f.createNewFile();
            printMessage("The file " + f.getName() + " was created");
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String zeile = null;

            while ((zeile = reader.readLine()) != null) {
                leaderList.add(zeile);
            }
        } catch (Exception e) {
            System.out.println("ERROR in peerReadFile(): " + e.getMessage());
        }

        return leaderList;
    }

    /**
     * send a message to other when the election of the leader was started
     *
     * @param hostname the hostname of the client
     * @param tcpPort  the port of the client
     * @throws Exception by getting an error in the leader setting
     */
    private synchronized static void sendlectionRequest(String hostname, int tcpPort) throws Exception {
        highestElectID = ID;

        for (Socket neighborSocket : neighborSockets) {
            if (!neighborSocket.isClosed()) {
                DataOutputStream outToServer = new DataOutputStream(neighborSocket.getOutputStream());
                outToServer.writeBytes("06-" + hostname + ":" + tcpPort + "-" + ID + "-" + TIME_TO_LIVE + '\n');
                outToServer.flush();

                if (!FLAG_Election_started) {
                    FLAG_Election_started = true;

                    timer.schedule(timerTask = new TimerTask() {
                        @Override
                        public void run() {
                            if (electionCounter == 0 && highestElectID <= ID) {
                                printMessage("Have chosen me as leader and send info to the net!");
                                try {
                                    setLeaderToList(hostname, tcpPort);
                                    sendElectionFinishedInformation(hostname, tcpPort, TIME_TO_LIVE + 1, "", 0);
                                    FLAG_Election_started = false;
                                    electionCounter = 0;
                                    highestElectID = -1;
                                    IDCounter = ID + 1;

                                    for (String neighbour : neighbours) {
                                        clients.add(neighbour.split(":")[0] + ":" + tcpPort);
                                    }

                                    Thread.sleep(3000);
                                    berkley(TIME_TO_LIVE);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }, 7000);

                }
            }
        }

    }

    /**
     * start a request of special command like files, quit ...
     *
     * @param hostname the hostname of the client
     * @param port     the port of the client
     * @throws Exception by client was not found
     */
    private static void sendRequest(String hostname, int port) throws Exception {
        while (true) {
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            printMessage("What message would you like to send?");
            String s = inFromUser.readLine();
            if (s.startsWith("exit") || s.startsWith("03")) {
                try {
                    String leader = peerReadFile().get(0);
                    Socket socket = new Socket(leader.split(":")[0], Integer.parseInt(leader.split(":")[1]));
                    DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
                    outToServer.writeBytes("03-" + hostname + ":" + port + '\n');
                    outToServer.flush();
                    BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream())));
                    String modifiedSentence = br.readLine();
                    printMessage("FROM SERVER: " + modifiedSentence);
                    socket.close();
                    for (int i = 0; i < neighborSockets.size(); i++) {
                        neighbours.remove(i);
                        neighborSockets.get(i).close();
                        neighborSockets.remove(i);
                    }
                } catch (Exception e) {
                    printMessage("The registered leader is unknown.");
                    System.exit(0);
                }
                System.exit(0);
            } else if (s.startsWith("sync") || s.startsWith("zeit") || s.startsWith("150")) {
                printMessage("INFO: The time synchronization is now started...");
                berkley(TIME_TO_LIVE);
            } else if (s.startsWith("search") || s.startsWith("04") || s.startsWith("finde") || s.startsWith("suche")) {
                String[] argContainer = s.split(" ");
                String msg = "";

                if (argContainer.length == 2) {
                    sendSearchMessageToNeighbours(Integer.parseInt(argContainer[1]), hostname, port, TIME_TO_LIVE, null);
                    FLAG_Search = true;
                } else {
                    printMessage("Error in the syntax: 'search <ID>'");
                }
                message = msg;
            } else if (s.startsWith("send") || s.startsWith("sende") || s.startsWith("nachricht") || s.startsWith("message")) {
                //send -n <neighbour number>
                //send -ip <IP-address>
                //send -id <ID>
                //election <leader election>
                String[] argContainer = s.split(" ");

                if (argContainer[1].equals("-n")) {
                    try {
                        if (Integer.parseInt(argContainer[2]) >= 0 && Integer.parseInt(argContainer[2]) < 4) {
                            String msg = "";
                            for (int i = 3; i < argContainer.length; i++) {
                                msg += argContainer[i] + " ";
                            }
                            printMessage("Message: " + msg);
                            Socket socket = neighborSockets.get(Integer.parseInt(argContainer[2]));
                            if (!socket.isClosed()) {
                                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                                dos.writeBytes(msg + "\n");
                                dos.flush();
                            } else {
                                printMessage("The socket is not open!");
                            }
                            messageIP.add(socket.getInetAddress() + ":" + socket.getPort());
                        } else {
                            printMessage("Incorrect command: send -n <neighbour number>");
                        }
                    } catch (Exception e) {
                        printMessage("Could not be found!");
                    }

                } else if (argContainer[1].equals("-ip")) {
                    if (argContainer[2].split(":")[0].length() > 6 && argContainer[2].split(":")[0].length() < 16) {
                        String msg = "";
                        for (int i = 3; i < argContainer.length; i++) {
                            msg += argContainer[i] + " ";
                        }
                        printMessage("Message: " + msg);
                        try {
                            Socket socket = new Socket(argContainer[2].split(":")[0], Integer.parseInt(argContainer[2].split(":")[1]));
                            if (!socket.isClosed()) {
                                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                                dos.writeBytes(msg + "\n");
                                dos.flush();

                                DataInputStream dis = new DataInputStream(socket.getInputStream());
                                BufferedReader br = new BufferedReader(new InputStreamReader(new DataInputStream(socket.getInputStream())));
                                String gotMsg = br.readLine();

                                printMessage(gotMsg);
                                Thread t = new Thread() {
                                    public void run() {
                                        if (neighbours.size() < 4) {
                                            boolean isNeighbour = false;
                                            for (String neighbour : neighbours) {
                                                if (neighbour.split(":")[0].equals(argContainer[2].split(":")[0])) {
                                                    isNeighbour = true;
                                                    break;
                                                }
                                            }
                                            if (!isNeighbour) {
                                                neighbours.add(argContainer[2]);
                                                neighborSockets.add(socket);
                                            }
                                            listenToServerRequest(socket, dos, dis, port, hostname);
                                        }
                                    }
                                };
                                //is blocking the communication of the message sender
                                //t.start();

                            } else {
                                printMessage("Socket closed");
                            }
                        } catch (Exception e) {
                            printMessage("The requested client was not reached");
                        }
                    } else {
                        printMessage("The IP address has an invalid format.");
                    }

                } else if (argContainer[1].equals("-id")) {
                    StringBuilder msg = new StringBuilder();
                    sendSearchMessageToNeighbours(Integer.parseInt(argContainer[2]), hostname, port, TIME_TO_LIVE, null);
                    FLAG_Message = true;
                    for (int i = 3; i < argContainer.length; i++) {
                        msg.append(argContainer[i]).append(" ");
                    }
                    message = msg.toString();
                } else {
                    printMessage("The entry was invalid!");
                }
            } else if (s.startsWith("-election") || s.startsWith("election") || s.startsWith("-leaderelection") || s.startsWith("leaderelection")) {
                printMessage("INFO: The leader selection is now started...");
                sendlectionRequest(hostname, port);
            } else if (s.startsWith("-help") || s.startsWith("help") || s.startsWith("-hilfe") || s.startsWith("hilfe") || s.startsWith("use help")) {
                showHelp();
            } else if (s.startsWith("-credit") || s.startsWith("credits") || s.startsWith("-credits") || s.startsWith("credit")) {
                showCredits();
            } else if (s.startsWith("file") || s.startsWith("datei")) {
                String[] argContainer = s.split(" ");
                if (argContainer[1].equalsIgnoreCase("-search") && argContainer.length > 2) {
                    String msg = "101-";
                    for (int i = 2; i < argContainer.length; i++) {
                        if (i == (argContainer.length - 1))
                            msg += argContainer[i];
                        else msg += argContainer[i] + "-";
                    }
                } else if (argContainer[1].equalsIgnoreCase("-send") && argContainer.length >= 5) {
                    if (argContainer[2].equalsIgnoreCase("-ip")) {
                        if (argContainer[2].split(":")[0].length() > 6 && argContainer[2].split(":")[0].length() < 16) {
                            try {
                                Socket socket = new Socket(argContainer[3].split(":")[0], Integer.parseInt(argContainer[3].split(":")[1]));
                                sendFile1(socket, argContainer[4]);
                            } catch (Exception e) {
                                printMessage("An error has occurred during file transfer!");
                            }
                        }
                    } else if (argContainer[2].equalsIgnoreCase("-id")) {
                        sendSearchMessageToNeighbours(Integer.parseInt(argContainer[3]), hostname, port, TIME_TO_LIVE, null);
                        searchWithMessageTransfer.put(Integer.parseInt(argContainer[3]), "filename:" + argContainer[4]);
                    } else if (argContainer[2].equalsIgnoreCase("-n")) {
                        Socket socket = neighborSockets.get(Integer.parseInt(argContainer[3]));
                        sendFile1(socket, argContainer[4]);
                    }

                } else if (argContainer[1].equalsIgnoreCase("-create") && argContainer.length >= 4) {
                    File f = new File(System.getProperty("user.home").replace("\\", "/") + "/" + argContainer[2]);
                    if (!f.exists()) {
                        f.createNewFile();
                        for (int i = 3; i < argContainer.length; i++) {
                            fileTags.put(argContainer[i], argContainer[2]);
                        }
                        printMessage("The file has been created and is ready for editing! Use 'file -update <filename> <TEXT...>");

                    } else {
                        printMessage("ATTENTION: The file already exists!");
                    }
                } else if (argContainer[1].equalsIgnoreCase("-download") && argContainer.length == 4) {
                    //file -download <IP:Port> <Filename>
                    //file -download <ID> <filename>

                    downloadFileFromIp(hostname, port, argContainer[2], argContainer[3]);
                } else if (argContainer[1].equalsIgnoreCase("-update") && argContainer.length > 3) {
                    File f = new File(System.getProperty("user.home").replace("\\", "/") + "/" + argContainer[2]);
                    if (!f.exists()) {
                        System.out.println("The file does not exist yet, please use: 'file -create <filename> <TAGS>");
                    } else {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(System.getProperty("user.home").replace("\\", "/") + "/" + argContainer[2]));
                        for (int i = 3; i < argContainer.length; i++)
                            writer.write(argContainer[i]);
                        writer.close();
                    }
                    sendFileInformation(argContainer[2], hostname + ":" + TCP_PORT, TIME_TO_LIVE, null);
                } else {
                    printMessage("The file command was invalid!");
                }
            } else if (s.startsWith("changetime")) {
                String[] argContainer = s.split(" ");
                if (argContainer.length > 1) {
                    if (argContainer[1].equals(("+"))) {
                        if (argContainer[2].equals("-h")) {
                            systemTime = systemTime.plusHours(Long.parseLong(argContainer[3]));
                            System.out.println("System time increased by hours");
                        }
                        if (argContainer[2].equals("-m")) {
                            systemTime = systemTime.plusMinutes(Long.parseLong(argContainer[3]));
                            System.out.println("System time increased by minutes");
                        }
                        if (argContainer[2].equals("-s")) {
                            systemTime = systemTime.plusSeconds(Long.parseLong(argContainer[3]));
                            System.out.println("System time increased by seconds");
                        }
                    } else {
                        if (argContainer[2].equals("-h")) {
                            systemTime = systemTime.minusHours(Long.parseLong(argContainer[3]));
                            System.out.println("System time reduced by hours");
                        }
                        if (argContainer[2].equals("-m")) {
                            systemTime = systemTime.minusMinutes(Long.parseLong(argContainer[3]));
                            System.out.println("System time reduced by minutes");
                        }
                        if (argContainer[2].equals("-s")) {
                            systemTime = systemTime.minusSeconds(Long.parseLong(argContainer[3]));
                            System.out.println("System time reduced by seconds");
                        }
                    }
                }
            } else if (s.startsWith("network")) {
                FLAG_Networking = true;
                for (int i = 0; FLAG_Networking; i++) {
                    try {
                        sendSearchMessageToNeighbours(i, hostname, port, TIME_TO_LIVE, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Thread.sleep(200);
                }
            } else if (s.startsWith("sysTime")) {
                System.out.printf("The current system time is: %s", systemTime.toString());

            } else {
                printMessage("The entry was not valid!");
            }
        }
    }

    /**
     * download of the file which was received by an ip
     *
     * @param hostname   optional modification
     * @param port       optional modification
     * @param fileHolder the owner of the file
     * @param filename   the name of the transmitted file
     */
    private synchronized static void downloadFileFromIp(String hostname, int port, String fileHolder, String filename) {
        try {
            Socket socket = new Socket(fileHolder.split(":")[0], Integer.parseInt(fileHolder.split(":")[1]));
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            FileInputStream fis = new FileInputStream(System.getProperty("user.home").replace("\\", "/") + filename);
            dos.writeBytes("103-filename\n");
            dos.flush();
            fis.readAllBytes();
            socket.close();
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    /**
     * send message to neighbours when a client was searched
     *
     * @param id               the received id of the client which send the message
     * @param requesterIP      the ip of the client which send the message
     * @param requesterPort    the port the client which send the message
     * @param TTL              the received time to live
     * @param connectionSocket the socket of the connection
     * @throws IOException by reading the file
     */
    public static synchronized void sendSearchMessageToNeighbours(int id, String requesterIP, int requesterPort, int TTL, Socket connectionSocket) throws IOException {
        if (p2pClient4.ID == id) {
            printMessage("INFO: Self inquiries are ignored!");
            return;
        }

        boolean isAlreadySearched = false;
        for (int i = 0; i < searchObjects.size(); i++) {
            if (searchObjects.get(i).getHostOfRequester().equals(requesterIP + ":" + requesterPort) && searchObjects.get(i).getID() == id) {
                Timestamp ts = new Timestamp(System.currentTimeMillis());
                if ((ts.getTime() - searchObjects.get(i).getTime()) < 10000) {
                    isAlreadySearched = true;
                }
            }
        }

        if (!isAlreadySearched) {
            for (int i = 0; i < neighborSockets.size(); i++) {
                Socket socket = neighborSockets.get(i);
                if (connectionSocket != null) {
                    if (socket.getInetAddress().equals(connectionSocket.getInetAddress()) && socket.getPort() == connectionSocket.getPort()) {
                        continue;
                    }
                }
                if (!socket.isClosed()) {
                    try {
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        dos.writeBytes("04-" + id + "-" + requesterIP + ":" + requesterPort + "-" + TTL + "\n");

                        dos.flush();
                    } catch (Exception e) {
                        printMessage("The desired partner could not be found.");
                    }
                } else {
                    neighbours.remove(i);
                    neighborSockets.get(i).close();
                    neighborSockets.remove(i);
                    printMessage("INFO: We have lost the neighbour from the list!");
                }
            }
            p2pSearchObjectBerkley p2pSO = new p2pSearchObjectBerkley();
            p2pSO.setID(id);
            p2pSO.setHostOfRequester(requesterIP + ":" + requesterPort);
            p2pSO.setTime(new Timestamp(System.currentTimeMillis()).getTime());
            searchObjects.add(p2pSO);
        }
    }

    /**
     * send election finishing information to all
     *
     * @param leaderIP      the leader ip
     * @param leaderPort    the leader port
     * @param TTL           the received time to live
     * @param neighbourIP   the ip of the neighbour
     * @param neighbourPort the port of the neighbour
     */
    public synchronized static void sendElectionFinishedInformation(String leaderIP, int leaderPort, int TTL, String neighbourIP, int neighbourPort) {
        try {
            if (TTL > 0) {
                TTL = TTL - 1;
                for (int i = 0; i < neighborSockets.size(); i++) {
                    Socket s = neighborSockets.get(i);
                    if (!neighbourIP.equals(s.getInetAddress().toString()) && !(neighbourPort == s.getPort()))
                        if (!s.isClosed()) {
                            DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                            dos.writeBytes("08-" + leaderIP + ":" + leaderPort + "-" + TTL + "\n");
                            dos.flush();
                        } else {
                            throw new Exception();
                        }
                }
            }
        } catch (Exception e) {
            printMessage("Neighbour not reached!");
        }
    }

    /**
     * send file
     *
     * @param connectionSocket socket of the connection
     * @param file             the transmitted file
     */
    public synchronized static void sendFile1(Socket connectionSocket, String file) {
        try {
            byte content[] = new byte[2048];
            FileInputStream fis = new FileInputStream(System.getProperty("user.home").replace("\\", "/") + "/" + file);
            fis.read(content, 0, content.length);
            OutputStream os = connectionSocket.getOutputStream();

            os.write("102-".getBytes());
            os.write((file + "-").getBytes());
            os.write(content, 0, content.length);
            os.write("\n".getBytes());
            os.flush();
        } catch (Exception e) {
            printMessage("The file was not found in the path!");
        }
    }

    /**
     * receiving the files
     *
     * @param clientSentence received text
     */
    public synchronized static void receiveFile(String clientSentence) {
        try {
            byte content[] = new byte[2048];
            String[] msg = clientSentence.split("-");
            File file = new File(System.getProperty("user.home").replace("\\", "/") + "/" + msg[1]);

            if (!file.exists()) {
                file.createNewFile();
            }

            content = clientSentence.split("-")[2].getBytes();
            Arrays.fill(content, (byte) 0);
            FileOutputStream fos = new FileOutputStream(System.getProperty("user.home") + "/" + file);
            fos.write(content, 0, content.length);
            actualFiles.add(msg[1] + "-" + msg[3]);
        } catch (Exception e) {
            printMessage("The file could not be received!");
        }
    }

    /**
     * the credits show
     */
    public static void showCredits() {
        System.out.println("\n  +-----------------------------------------------------------------------+");
        System.out.println("  |\t\t\t\t\t\t\t\t\t  |");
        System.out.println("  |\t.----------------.  .----------------.  .----------------. \t  |");
        System.out.println("  |\t| .--------------. || .--------------. || .--------------. |\t  |");
        System.out.println("  |\t| |   ______     | || |    _____     | || |   ______     | |\t  |");
        System.out.println("  |\t| |  |_   __ \\   | || |   / ___ `.   | || |  |_   __ \\   | |\t  |");
        System.out.println("  |\t| |    | |__) |  | || |  |_/___) |   | || |    | |__) |  | |\t  |");
        System.out.println("  |\t| |    |  ___/   | || |   .'____.'   | || |    |  ___/   | |\t  |");
        System.out.println("  |\t| |   _| |_      | || |  / /____     | || |   _| |_      | |\t  |");
        System.out.println("  |\t| |  |_____|     | || |  |_______|   | || |  |_____|     | |\t  |");
        System.out.println("  |\t| |              | || |              | || |              | |\t  |");
        System.out.println("  |\t| '--------------' || '--------------' || '--------------' |\t  |");
        System.out.println("  |\t'----------------'  '----------------'  '----------------' \t  |");
        System.out.println("  |\t\t\t\t\t\t\t\t\t  |");
        System.out.println("  |\t  |     (` _ |    _|-.  ,_  |)| _    _ | \t\t\t  |");
        System.out.println("  |\t  |)\\/  _)(/_|)(|_\\|_|(|||  |)|(/_L|(/_|,\t\t\t  |");
        System.out.println("  |\t    /                                    \t\t\t  |");
        System.out.println("  |\t\t/`|_  . _|-.  ,_  |\\ _  _|_  ,_|-\t\t\t  |");
        System.out.println("  |\t\t\\,|||`|_\\|_|(|||  |/(/_(_||(|||| \t\t\t  |");
        System.out.println("  |\t\t\t\t\t\t\t\t\t  |");
        System.out.println("  +-----------------------------------------------------------------------+");
        System.out.println("  |\t\t\t\t\t_       _  \t\t\t  |");
        System.out.println("  |\t\t\t _ _ ___ ___   | |_ ___| |___ \t\t\t  |");
        System.out.println("  |\t\t\t| | |_ -| -_|  |   | -_| | . |\t\t\t  |");
        System.out.println("  |\t\t\t|___|___|___|  |_|_|___|_|  _|\t\t\t  |");
        System.out.println("  |\t\t\t\t\t\t |_| \t\t\t  |");
        System.out.println("  +-----------------------------------------------------------------------+\n");
    }

    /**
     * Help Info
     */
    public static void showHelp() {
        System.out.println("\n +----------------------------------------------------------------------------+");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |.----------------. .----------------. .----------------. .----------------. |");
        System.out.println(" || .--------------. | .--------------. | .--------------. | .--------------. |");
        System.out.println(" || |  ____  ____  | | |  _________   | | |   _____      | | |   ______     | |");
        System.out.println(" || | |_   ||   _| | | | |_   ___  |  | | |  |_   _|     | | |  |_   __ \\   | |");
        System.out.println(" || |   | |__| |   | | |   | |_  \\_|  | | |    | |       | | |    | |__) |  | |");
        System.out.println(" || |   |  __  |   | | |   |  _|  _   | | |    | |   _   | | |    |  ___/   | |");
        System.out.println(" || |  _| |  | |_  | | |  _| |___/ |  | | |   _| |__/ |  | | |   _| |_      | |");
        System.out.println(" || | |____||____| | | | |_________|  | | |  |________|  | | |  |_____|     | |");
        System.out.println(" || |              | | |              | | |              | | |              | |");
        System.out.println(" || '--------------' | '--------------' | '--------------' | '--------------' |");
        System.out.println(" |'----------------' '----------------' '----------------' '----------------' |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tTime-Syncronisation: \tsync\t\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tLeaderelection: \telection\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tExit network: \texit\t\t\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tsearch ID: \t\tsearch <ID>\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tsend message\t\t\t\t\t\t\t      |");
        System.out.println(" |\t\tNeighbour: \tsend -n <ID> <msg>\t\t\t      |");
        System.out.println(" |\t\tID: \t\tsend -id <ID> <msg>\t\t\t      |");
        System.out.println(" |\t\tIP: \t\tsend -ip <IP:Port> <msg>\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tSystem-time: \t\tsystime\t\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tchange-time: \t\tchangetime <+/-> -<h/m/s> <0...n>\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tsend-file\t\t\t\t\t\t\t      |");
        System.out.println(" |\t\t-search  <Tag/Filename>\t\t\t\t\t      |");
        System.out.println(" |\t\t-send   -<n/id/ip> <Filename>\t\t\t\t      |");
        System.out.println(" |\t\t-create <Filename> <Tags>\t\t\t\t      |");
        System.out.println(" |\t\t-update <Filename> <Text>\t\t\t\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" |\tExamples\t\t\t\t\t\t\t      |");
        System.out.println(" |\t\tchangetime + ex-m 5 \t//add 5 minutes\t\t\t      |");
        System.out.println(" |\t\tsend -n 2 Test   \t//send 'Test' to neighbour 2\t      |");
        System.out.println(" |\t\t\t\t\t\t\t\t\t      |");
        System.out.println(" +----------------------------------------------------------------------------+\n");
    }
}

/**
 * separated handler class
 */
class p2pSearchObjectBerkley {

    private String hostOfRequester;
    private long time;
    private int ID;

    public String getHostOfRequester() {
        return hostOfRequester;
    }

    public void setHostOfRequester(String hostOfRequester) {
        this.hostOfRequester = hostOfRequester;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }


    public p2pSearchObjectBerkley() {
    }
}