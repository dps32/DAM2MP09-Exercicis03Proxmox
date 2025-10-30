package com.client;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class CtrlResult implements Initializable {

    @FXML
    public Label txtFinalWinner;

    @FXML
    public Label txtPlayerName;

    @FXML
    public Label txtFinalScore;

    @FXML
    public Button btnMenu;

    @FXML
    public Button btnRematch;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // configurar la vista con los datos del ganador final
        updateResultInfo();
    }

    // actualizar la informacion del resultado final
    public void updateResultInfo() {
        // obtener datos del servidor
        JSONObject serverData = Main.lastServerData;
        
        if (serverData == null) {
            return;
        }

        // obtener el ganador final
        String gameWinner = serverData.optString("gameWinner", "");
        int scoreR = serverData.optInt("scoreR", 0);
        int scoreY = serverData.optInt("scoreY", 0);

        // buscar el nombre del ganador
        String winnerName = "";
        if (gameWinner.equals("R")) {
            winnerName = "Charizard"; // nombre del jugador rojo
        } else if (gameWinner.equals("Y")) {
            winnerName = "Pikachu"; // nombre del jugador amarillo
        }

        // mostrar el nombre del ganador
        txtPlayerName.setText(winnerName);

        // mostrar la puntuacion final
        txtFinalScore.setText("Rojo: " + scoreR + " - Amarillo: " + scoreY);
    }

    // boton menu
    @FXML
    private void onMenu() {
        // cerrar conexion primero
        if (Main.wsClient != null) {
            Main.wsClient.forceExit();
            Main.wsClient = null;
        }
        
        // limpiar datos del cliente
        Main.clientName = "";
        Main.clients = null;
        Main.objects = null;
        Main.lastServerData = null;
        Main.isMyTurn = false;
        
        // resetear animaciones si existe el controlador
        if (Main.ctrlPlay != null) {
            Main.ctrlPlay.stop();
            Main.ctrlPlay.resetAnimations();
        }
        
        // resetear la vista de configuracion
        if (Main.ctrlConfig != null) {
            Main.ctrlConfig.resetView();
        }
        
        // volver al menu principal: forzamos el cambio de vista en el hilo de UI
        Platform.runLater(() -> {
            UtilsViews.setView("ViewConfig");
        });
    }

    // boton revancha
    @FXML
    private void onRematch() {
        // limpiar datos anteriores
        Main.clients = null;
        Main.objects = null;
        Main.lastServerData = null;
        Main.isMyTurn = false;
        
        // resetear animaciones si existe el controlador
        if (Main.ctrlPlay != null) {
            Main.ctrlPlay.stop();
            Main.ctrlPlay.resetAnimations();
        }
        
        // resetear la vista de configuracion
        if (Main.ctrlConfig != null) {
            Main.ctrlConfig.resetView();
        }
        
        // volver a la vista de configuracion y reconectar
        Platform.runLater(() -> {
            UtilsViews.setView("ViewConfig");
            
            // reconectar al servidor automaticamente
            Main.pauseDuring(100, () -> {
                Main.connectToServer();
            });
        });
    }
}
