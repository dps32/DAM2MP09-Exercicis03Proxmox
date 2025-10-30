package com.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.ClientData;
import com.shared.GameObject;

// Servidor de Conecta 4 con WebSocket
public class Main extends WebSocketServer {

    // puerto del servidor
    public static final int DEFAULT_PORT = 3000;

    // nombres de jugadores
    private static final List<String> PLAYER_NAMES = Arrays.asList(
        "Bulbasaur", "Charizard", "Blaziken", "Umbreon", "Mewtwo", "Pikachu", "Wartortle"
    );

    // colores de jugadores
    private static final List<String> PLAYER_COLORS = Arrays.asList(
        "GREEN", "ORANGE", "RED", "GRAY", "PURPLE", "YELLOW", "BLUE"
    );

    // cuantos jugadores se necesitan para empezar
    private static final int REQUIRED_CLIENTS = 2;

    // nombres de campos JSON
    private static final String K_TYPE = "type";
    private static final String K_VALUE = "value";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENTS_LIST = "clientsList";             
    private static final String K_OBJECTS_LIST = "objectsList"; 

    // tipos de mensajes
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";
    private static final String T_CLIENT_OBJECT_MOVING = "clientObjectMoving";
    private static final String T_CLIENT_PIECE_MOVING = "clientPieceMoving";
    private static final String T_CLIENT_PLAY = "clientPlay";
    private static final String T_CLIENT_CONTINUE_ROUND = "clientContinueRound";
    private static final String T_CLIENT_REMATCH = "clientRematch";
    private static final String T_SERVER_DATA = "serverData";
    private static final String T_COUNTDOWN = "countdown";

    // registro de clientes conectados
    private final ClientRegistry clients;

    // datos de cada cliente (nombre, color, posicion, rol)
    private final Map<String, ClientData> clientsData = new HashMap<>();

    // todas las fichas del juego
    private final Map<String, GameObject> gameObjects = new HashMap<>();

    // para saber si ya esta corriendo la cuenta atras
    private volatile boolean countdownRunning = false;

    // cuantas veces por segundo enviar datos
    private static final int SEND_FPS = 60;
    private final ScheduledExecutorService ticker;
    
    // tablero del conecta 4
    private final String[][] board = new String[6][7]; // 6 filas x 7 columnas
    private String currentTurn = "R"; // de quien es el turno (R o Y)
    
    // puntuacion de cada jugador
    private int scoreR = 0; // victorias del rojo
    private int scoreY = 0; // victorias del amarillo
    
    // ganador de la ronda actual
    private String roundWinner = null; // "R", "Y" o null
    
    // ganador final (quien llega a 3)
    private String gameWinner = null; // "R", "Y" o null
    
    // posicion y tamaño del tablero
    private static final double GRID_START_X = 25;
    private static final double GRID_START_Y = 25;
    private static final double CELL_SIZE = 50;
    private static final int GRID_ROWS = 6;
    private static final int GRID_COLS = 7;

    // crear el servidor
    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);
        initializegameObjects();
        initializeBoard();

        // crear el ticker para enviar datos cada frame
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(tf);
    }

    // crear todas las fichas al inicio
    private void initializegameObjects() {
        // el tablero termina en x=375, poner las fichas a la derecha
        double piecesStartX = 450;
        double piecesStartY = 50;
        double verticalSpacing = 45;
        
        // crear 21 fichas rojas
        for (int i = 0; i < 21; i++) {
            String objId = "R_" + String.format("%02d", i);
            int x = (int) (piecesStartX + (i % 2) * 45);
            int y = (int) (piecesStartY + (i / 2) * verticalSpacing);
            GameObject obj = new GameObject(objId, x, y, 1, 1);
            gameObjects.put(objId, obj);
        }
        
        // crear 21 fichas amarillas
        for (int i = 0; i < 21; i++) {
            String objId = "Y_" + String.format("%02d", i);
            int x = (int) (piecesStartX + 90 + (i % 2) * 45);
            int y = (int) (piecesStartY + (i / 2) * verticalSpacing);
            GameObject obj = new GameObject(objId, x, y, 1, 1);
            gameObjects.put(objId, obj);
        }
    }

    // limpiar el tablero
    private void initializeBoard() {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                board[row][col] = " ";
            }
        }
    }

    // reiniciar el juego
    private synchronized void resetGame() {
        // limpiar tablero
        initializeBoard();
        
        // empezar con rojo
        currentTurn = "R";
        
        // limpiar ganador de ronda
        roundWinner = null;
        
        // volver a poner las fichas en su sitio
        gameObjects.clear();
        initializegameObjects();
    }
    
    // reiniciar puntuacion completa
    private synchronized void resetScores() {
        scoreR = 0;
        scoreY = 0;
        gameWinner = null;
        resetGame();
    }
    
    // comprobar si hay 4 en linea
    private boolean checkWinner(int row, int col, String player) {
        // horizontal
        int count = 0;
        for (int c = 0; c < GRID_COLS; c++) {
            if (board[row][c].equals(player)) {
                count++;
                if (count >= 4) {
                    return true;
                }
            } else {
                count = 0;
            }
        }
        
        // vertical
        count = 0;
        for (int r = 0; r < GRID_ROWS; r++) {
            if (board[r][col].equals(player)) {
                count++;
                if (count >= 4) {
                    return true;
                }
            } else {
                count = 0;
            }
        }
        
        // diagonal \ (arriba-izq a abajo-der)
        count = 0;
        int startRow = row - Math.min(row, col);
        int startCol = col - Math.min(row, col);
        while (startRow < GRID_ROWS && startCol < GRID_COLS) {
            if (board[startRow][startCol].equals(player)) {
                count++;
                if (count >= 4) {
                    return true;
                }
            } else {
                count = 0;
            }
            startRow++;
            startCol++;
        }
        
        // diagonal / (abajo-izq a arriba-der)
        count = 0;
        startRow = row + Math.min(GRID_ROWS - 1 - row, col);
        startCol = col - Math.min(GRID_ROWS - 1 - row, col);
        while (startRow >= 0 && startCol < GRID_COLS) {
            if (board[startRow][startCol].equals(player)) {
                count++;
                if (count >= 4) {
                    return true;
                }
            } else {
                count = 0;
            }
            startRow--;
            startCol++;
        }
        
        return false;
    }

    // procesar una jugada
    private synchronized boolean processPlay(String clientName, int column, String pieceId) {
        // buscar el cliente
        ClientData client = clientsData.get(clientName);
        if (client == null || client.role == null) {
            return false;
        }

        // ver si es su turno
        if (!client.role.equals(currentTurn)) {
            return false;
        }

        // columna valida?
        if (column < 0 || column >= GRID_COLS) {
            return false;
        }

        // buscar la fila mas baja que este vacia
        int targetRow = -1;
        for (int row = GRID_ROWS - 1; row >= 0; row--) {
            if (board[row][column].equals(" ")) {
                targetRow = row;
                break;
            }
        }

        // columna llena?
        if (targetRow == -1) {
            return false;
        }


        // poner la ficha en el tablero
        board[targetRow][column] = client.role;
        
        // calcular la posicion en pixeles
        int gridX = (int) (GRID_START_X + column * CELL_SIZE + CELL_SIZE / 2);
        int gridY = (int) (GRID_START_Y + targetRow * CELL_SIZE + CELL_SIZE / 2);

        // mover la ficha especifica a su posicion final
        if (pieceId != null && !pieceId.isEmpty() && gameObjects.containsKey(pieceId)) {
            GameObject piece = gameObjects.get(pieceId);
            
            piece.x = gridX;
            piece.y = gridY;
        } else {
            return false;
        }
        
        // comprobar si hay ganador
        if (checkWinner(targetRow, column, client.role)) {
            // hay ganador esta ronda
            roundWinner = client.role;
            
            // sumar punto
            if (client.role.equals("R")) {
                scoreR++;
            } else {
                scoreY++;
            }
            
            // ver si ha ganado la partida (solo necesita 1 ronda)
            if (scoreR >= 1) {
                gameWinner = "R";
            } else if (scoreY >= 1) {
                gameWinner = "Y";
            }
            
            // no cambiar turno, la ronda acabo
            return true;
        }

        // cambiar el turno
        if (currentTurn.equals("R")) {
            currentTurn = "Y";
        } else {
            currentTurn = "R";
        }

        return true;
    }

    // conseguir el color de un jugador
    private synchronized String getColorForName(String name) {
        int idx = PLAYER_NAMES.indexOf(name);
        if (idx < 0) {
            idx = 0;
        }
        return PLAYER_COLORS.get(idx % PLAYER_COLORS.size());
    }

    // hacer la cuenta atras antes de empezar
    private void sendCountdown() {
        synchronized (this) {
            if (countdownRunning) {
                return;
            }
            if (clients.snapshot().size() != REQUIRED_CLIENTS) {
                return;
            }
            countdownRunning = true;
        }

        // reiniciar el juego cuando empiece la cuenta atras
        resetGame();

        new Thread(() -> {
            try {
                for (int i = 5; i >= 0; i--) {
                    // si se desconecta alguien, parar
                    if (clients.snapshot().size() < REQUIRED_CLIENTS) {
                        break;
                    }

                    sendCountdownToAll(i);
                    if (i > 0) {
                        Thread.sleep(750);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                countdownRunning = false;
            }
        }, "CountdownThread").start();
    }

    // crear un mensaje JSON
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    // enviar mensaje a un cliente
    private void sendSafe(WebSocket to, String payload) {
        if (to == null) {
            return;
        }
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            String name = clients.cleanupDisconnected(to);
            clientsData.remove(name);
        } catch (Exception e) {
            // error de conexion
        }
    }

    // enviar a todos menos al que envia
    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!Objects.equals(conn, sender)) {
                sendSafe(conn, payload);
            }
        }
    }

    // enviar el estado del juego a todos
    private void broadcastStatus() {
        JSONArray arrClients = new JSONArray();
        for (ClientData c : clientsData.values()) {
            arrClients.put(c.toJSON());
        }

        JSONArray arrObjects = new JSONArray();
        for (GameObject obj : gameObjects.values()) {
            arrObjects.put(obj.toJSON());
        }

        JSONObject rst = msg(T_SERVER_DATA)
                        .put(K_CLIENTS_LIST, arrClients)
                        .put(K_OBJECTS_LIST, arrObjects)
                        .put("currentTurn", currentTurn)
                        .put("scoreR", scoreR)
                        .put("scoreY", scoreY);
        
        // añadir ganadores si existen
        if (roundWinner != null) {
            rst.put("roundWinner", roundWinner);
        }
        if (gameWinner != null) {
            rst.put("gameWinner", gameWinner);
        }

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            String name = clients.nameBySocket(conn);
            rst.put(K_CLIENT_NAME, name);
            sendSafe(conn, rst.toString());
        }
    }

    /** Envia a tots els clients el compte enrere. */
    private void sendCountdownToAll(int n) {
        JSONObject rst = msg(T_COUNTDOWN).put(K_VALUE, n);
        broadcastExcept(null, rst.toString());
    }

    // ----------------- WebSocketServer overrides -----------------

    /** Assigna un nom i color al client i envia l'STATE complet. */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        String color = getColorForName(name);
        
        // Assign role based on connection order
        String role = clientsData.size() == 0 ? "R" : "Y";

        ClientData cd = new ClientData(name, color);
        cd.role = role;
        clientsData.put(name, cd);
        
        System.out.println("[SERVER] Client connected: " + name + " (role: " + role + ", color: " + color + ")");

        System.out.println("WebSocket client connected: " + name + " (" + color + ") - Role: " + role);
        sendCountdown();
    }

    /** Elimina el client del registre i envia l'STATE complet. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);
        System.out.println("WebSocket client disconnected: " + name);
        
        // si queda menos de 2 jugadores, resetear el juego completo
        if (clientsData.size() < 2) {
            resetScores();
            System.out.println("[SERVER] Game reset - less than 2 players");
            // notificar al cliente restante (si existe) del nuevo estado
            broadcastStatus();
        }
    }

    /** Processa els missatges rebuts. */
    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // JSON invàlid
        }

        String type = obj.optString(K_TYPE, "");
        
        // ver que tipo de mensaje es
        if (type.equals(T_CLIENT_MOUSE_MOVING)) {
            // actualizar posicion del raton
            String clientName = clients.nameBySocket(conn);
            ClientData existingData = clientsData.get(clientName);
            ClientData newData = ClientData.fromJSON(obj.getJSONObject(K_VALUE));
            
            // mantener el rol
            if (existingData != null && existingData.role != null) {
                newData.role = existingData.role;
            }
            
            clientsData.put(clientName, newData);
            
        } else if (type.equals(T_CLIENT_OBJECT_MOVING)) {
            // mover objeto
            GameObject objData = GameObject.fromJSON(obj.getJSONObject(K_VALUE));
            gameObjects.put(objData.id, objData);
            
        } else if (type.equals(T_CLIENT_PIECE_MOVING)) {
            // ignorar movimientos mientras arrastra
            
        } else if (type.equals(T_CLIENT_PLAY)) {
            // procesar jugada
            String clientName = clients.nameBySocket(conn);
            int column = obj.optInt("column", -1);
            String pieceId = obj.optString("pieceId", "");
            processPlay(clientName, column, pieceId);
            
        } else if (type.equals(T_CLIENT_CONTINUE_ROUND)) {
            // continuar a la siguiente ronda
            resetGame();
            broadcastStatus(); // enviar el nuevo estado a todos
            
        } else if (type.equals(T_CLIENT_REMATCH)) {
            // revancha completa
            resetScores();
            broadcastStatus(); // enviar el nuevo estado a todos
        }
    }

    // cuando hay un error
    @Override
    public void onError(WebSocket conn, Exception ex) {
        // error de conexion
    }

    // cuando arranca el servidor
    @Override
    public void onStart() {
        setConnectionLostTimeout(100);
        startTicker();
    }

    // para cerrar el servidor correctamente
    private static void registerShutdownHook(Main server) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stopTicker();
                server.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    // mantener el servidor corriendo
    private static void awaitForever() {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // empezar a enviar datos cada frame
    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                // solo enviar si hay clientes conectados
                if (!clients.snapshot().isEmpty()) {
                    broadcastStatus();
                }
            } catch (Exception e) {
                // error al enviar
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    // parar el ticker
    private void stopTicker() {
        try {
            ticker.shutdownNow();
            ticker.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // programa principal
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        registerShutdownHook(server);
        awaitForever();
    }
}
