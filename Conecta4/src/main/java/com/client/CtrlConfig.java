package com.client;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public Label txtMessage;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // limpiar mensaje al inicializar
        if (txtMessage != null) {
            txtMessage.setText("");
        }
    }
    
    // metodo para resetear la vista cuando volvemos del juego
    public void resetView() {
        txtMessage.setText("");
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void connectToServer() {
        Main.connectToServer();
    }

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("vprietogallegos.ieti.site");
        txtPort.setText("443");
    }
}