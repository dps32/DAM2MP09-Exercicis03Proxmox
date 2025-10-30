package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.ClientData;
import com.shared.GameObject;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;


public class Main extends Application {

    public static UtilsWS wsClient;

    public static String clientName = "";
    public static List<ClientData> clients;
    public static List<GameObject> objects;
    public static boolean isMyTurn = false;
    public static JSONObject lastServerData = null; // datos del servidor

    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlWinner ctrlWinner;
    public static CtrlResult ctrlResult;

    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewWinner", "/assets/viewWinner.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlWinner = (CtrlWinner) UtilsViews.getController("ViewWinner");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        // Mostrar la vista inicial
        UtilsViews.setView("ViewConfig");

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.onCloseRequestProperty(); // Llamar metodo close al cerrar ventana
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Agregar icono solo si no es Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image("file:/icons/icon.png");
            stage.getIcons().add(icon);
        }
    }

    @Override
    public void stop() { 
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Finalizar todos los servicios ejecutores
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static <T> List<T> jsonArrayToList(JSONArray array, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            T value = clazz.cast(array.get(i));
            list.add(value);
        }
        return list;
    }

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");
    
        pauseDuring(1500, () -> { // Dar tiempo para mostrar mensaje de conexion

            String protocol = ctrlConfig.txtProtocol.getText();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            wsClient = UtilsWS.getSharedInstance(protocol + "://" + host + ":" + port);
    
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }
   
    private static void wsMessage(String response) {
        
        // System.out.println(response);
        
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "serverData":
                // guardar datos del servidor
                lastServerData = msgObj;
                
                clientName = msgObj.getString("clientName");

                JSONArray arrClients = msgObj.getJSONArray("clientsList");
                List<ClientData> newClients = new ArrayList<>();
                for (int i = 0; i < arrClients.length(); i++) {
                    JSONObject obj = arrClients.getJSONObject(i);
                    newClients.add(ClientData.fromJSON(obj));
                }
                clients = newClients;

                JSONArray arrObjects = msgObj.getJSONArray("objectsList");
                List<GameObject> newObjects = new ArrayList<>();
                for (int i = 0; i < arrObjects.length(); i++) {
                    JSONObject obj = arrObjects.getJSONObject(i);
                    newObjects.add(GameObject.fromJSON(obj));
                }
                
                // Detect pieces that moved to the board (for falling animation)
                if (objects != null && ctrlPlay != null) {
                    for (GameObject newObj : newObjects) {
                        // Buscar si este objeto existia antes
                        GameObject oldObj = objects.stream()
                            .filter(o -> o.id.equals(newObj.id))
                            .findFirst()
                            .orElse(null);
                        
                        // detectar si una ficha fue colocada en el tablero
                        // la ficha estaba fuera del tablero (x > 400) y ahora esta dentro (x < 400)
                        // O la Y cambio significativamente (fue colocada en una columna)
                        if (oldObj != null) {
                            boolean wasOffBoard = oldObj.x > 400;
                            boolean isOnBoard = newObj.x < 400;
                            boolean positionChanged = Math.abs(oldObj.y - newObj.y) > 5;
                            
                            // si la ficha paso de fuera a dentro del tablero
                            if (wasOffBoard && isOnBoard) {
                                // empezar animacion desde arriba de su columna
                                double startY = 50.0; // arriba del tablero (25 inicio + 25 centro celda)
                                double targetY = newObj.y; // posicion final del servidor
                                
                                ctrlPlay.startFallingAnimation(newObj.id, startY, targetY);
                            }
                        }
                    }
                }
                
                objects = newObjects;

                // comprobar si hay ganador de ronda o final
                String roundWinner = msgObj.optString("roundWinner", null);
                String gameWinner = msgObj.optString("gameWinner", null);
                
                String activeView = UtilsViews.getActiveView();
                
                if (gameWinner != null && !gameWinner.isEmpty()) {
                    // hay ganador final, desconectar del servidor y mostrar resultado
                    if (wsClient != null) {
                        wsClient.forceExit();
                        wsClient = null;
                    }
                    
                    ctrlResult.updateResultInfo();
                    if (activeView == null || !activeView.equals("ViewResult")) {
                        UtilsViews.setViewAnimating("ViewResult");
                    }
                } else if (roundWinner != null && !roundWinner.isEmpty()) {
                    // hay ganador de ronda, ir a vista de winner
                    ctrlWinner.updateWinnerInfo();
                    if (activeView == null || !activeView.equals("ViewWinner")) {
                        UtilsViews.setViewAnimating("ViewWinner");
                    }
                } else {
                    // no hay ganador, continuar juego normal
                    // si estabamos en vista de winner/result, volver a play
                    if (activeView != null && (activeView.equals("ViewWinner") || activeView.equals("ViewResult"))) {
                        // resetear animaciones para la nueva ronda
                        if (ctrlPlay != null) {
                            ctrlPlay.resetAnimations();
                            ctrlPlay.start();
                        }
                        UtilsViews.setViewAnimating("ViewPlay");
                    }
                    
                    // Actualizar informacion del turno
                    String currentTurn = msgObj.optString("currentTurn", "");
                    if (!currentTurn.isEmpty() && clients.size() > 0) {
                        ClientData myClient = clients.stream()
                            .filter(c -> c.name.equals(clientName))
                            .findFirst()
                            .orElse(null);
                        
                        if (myClient != null && myClient.role != null) {
                            isMyTurn = myClient.role.equals(currentTurn);
                            String colorName = currentTurn.equals("R") ? "ROJO" : "AMARILLO";
                            String turnText;
                            if (isMyTurn) {
                                turnText = "TU TURNO (" + colorName + ")";
                            } else {
                                turnText = "TURNO: " + colorName;
                            }
                            // verificar que ctrlPlay existe antes de actualizar
                            if (ctrlPlay != null && ctrlPlay.title != null) {
                                ctrlPlay.title.setText(turnText);
                            }
                        }
                    }
                }

                if (clients.size() == 1) {
                    // solo actualizar la lista de espera si estamos en esa vista
                    if (ctrlWait != null && ctrlWait.txtPlayer0 != null) {
                        ctrlWait.txtPlayer0.setText(clients.get(0).name);
                    }
                } else if (clients.size() > 1) {
                    // verificar que ctrlWait existe antes de actualizar
                    if (ctrlWait != null && ctrlWait.txtPlayer0 != null && ctrlWait.txtPlayer1 != null) {
                        ctrlWait.txtPlayer0.setText(clients.get(0).name);
                        ctrlWait.txtPlayer1.setText(clients.get(1).name);
                    }
                    // No sobrescribir el texto del turno aqui - ya esta establecido arriba
                }
                
                String activeViewAtEnd = UtilsViews.getActiveView();
                if (activeViewAtEnd != null && activeViewAtEnd.equals("ViewConfig")) {
                    UtilsViews.setViewAnimating("ViewWait");
                }

                break;
            
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 5) {
                    // Resetear animaciones cuando empieza nuevo juego (cuenta regresiva empieza en 5)
                    if (ctrlPlay != null) {
                        ctrlPlay.resetAnimations();
                    }
                }
                if (value == 0) {
                    // reiniciar el timer de animacion antes de mostrar la vista
                    if (ctrlPlay != null) {
                        ctrlPlay.start();
                    }
                    UtilsViews.setViewAnimating("ViewPlay");
                    txt = "GO";
                }
                if (ctrlWait != null && ctrlWait.txtTitle != null) {
                    ctrlWait.txtTitle.setText(txt);
                }
                break;
        }
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
