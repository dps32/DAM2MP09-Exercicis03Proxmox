package com.client;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class CtrlWinner implements Initializable {

    @FXML
    public Label txtWinner;

    @FXML
    public Label txtPlayerName;

    @FXML
    public Label txtScore;

    @FXML
    public Button btnContinue;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // configurar la vista con los datos del ganador
        updateWinnerInfo();
    }

    // actualizar la informacion del ganador
    public void updateWinnerInfo() {
        // obtener datos del servidor
        JSONObject serverData = Main.lastServerData;
        
        if (serverData == null) {
            return;
        }

        // obtener el ganador de la ronda
        String roundWinner = serverData.optString("roundWinner", "");
        int scoreR = serverData.optInt("scoreR", 0);
        int scoreY = serverData.optInt("scoreY", 0);

        // buscar el nombre del ganador
        String winnerName = "";
        if (roundWinner.equals("R")) {
            winnerName = "Charizard"; // nombre del jugador rojo
        } else if (roundWinner.equals("Y")) {
            winnerName = "Pikachu"; // nombre del jugador amarillo
        }

        // mostrar el nombre del ganador
        txtPlayerName.setText(winnerName);

        // mostrar la puntuacion
        txtScore.setText("Rojo: " + scoreR + " - Amarillo: " + scoreY);
    }

    // boton continuar
    @FXML
    private void onContinue() {
        // enviar mensaje al servidor para continuar
        JSONObject msg = new JSONObject();
        msg.put("type", "clientContinueRound");
        msg.put("clientName", Main.clientName);
        Main.wsClient.safeSend(msg.toString());
        
        // el servidor enviara el nuevo estado y Main.java se encargara de la transicion
    }
}
