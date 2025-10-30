package com.client;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.json.JSONObject;

import com.shared.ClientData;
import com.shared.GameObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlPlay implements Initializable {

    @FXML
    public javafx.scene.control.Label title;

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;

    // para saber si estoy arrastrando
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    // la ficha que he seleccionado
    private GameObject selectedObject = null;

    // tamaño de las fichas
    private final double PIECE_RADIUS = 20;
    private final double PIECE_MARGIN = 10;
    private final double PIECES_START_X_OFFSET = 80;
    
    // para hacer la animacion de caer
    private final Map<String, FallingPiece> fallingPieces = new HashMap<>();
    
    // para que no se repita la animacion
    private Set<String> animatedPieces = new HashSet<>();
    
    // clase para la animacion
    private static class FallingPiece {
        String pieceId;
        double currentY;
        double targetY;
        double velocity;
        
        FallingPiece(String pieceId, double startY, double targetY) {
            this.pieceId = pieceId;
            this.currentY = startY;
            this.targetY = targetY;
            this.velocity = 2.0; // velocidad inicial para que empiece suave
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // conseguir el contexto de dibujo
        this.gc = canvas.getGraphicsContext2D();

        // poner los listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // crear el tablero 7 columnas x 6 filas
        grid = new PlayGrid(25, 25, 50, 6, 7);

        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // cuando cambia el tamaño de la ventana
    public void onSizeChanged() {
        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // empezar el timer de animacion
    public void start() {
        animationTimer.start();
    }

    // parar el timer
    public void stop() {
        animationTimer.stop();
    }
    
    // reiniciar las animaciones para nueva partida
    public void resetAnimations() {
        animatedPieces.clear();
        fallingPieces.clear();
    }

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        String color = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.color)
            .findFirst()
            .orElse("gray");

        // crear los datos del cliente
        ClientData cd = new ClientData(
            Main.clientName, 
            color,
            (int)mouseX, 
            (int)mouseY,  
            grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getRow(mouseY) : -1,
            grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getCol(mouseX) : -1
        );

        // enviar al servidor
        JSONObject msg = new JSONObject();
        msg.put("type", "clientMouseMoving");
        msg.put("value", cd.toJSON());

        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msg.toString());
        }
    }

    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        selectedObject = null;
        mouseDragging = false;

        // comprobamos si es el turno del usuario actual
        if (!Main.isMyTurn) return;

        // nos aseguramos de que tenemos los datos necesarios del juego
        if (Main.clients == null || Main.objects == null) return;

        // obtenemos el rol asignado al usuario actual (R o Y)
        String myRole = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.role)
            .findFirst()
            .orElse("");

        if (myRole.isEmpty()) return;

        // buscamos si el usuario ha clickado dentro de alguna ficha de su color
        for (GameObject go : Main.objects) {
            if (go.role == null || !go.role.equals(myRole)) continue;
            
            // calculamos la distancia desde el cursor hasta el centro de la ficha
            double distance = Math.sqrt(Math.pow(mouseX - go.x, 2) + Math.pow(mouseY - go.y, 2));
            
            if (distance > PIECE_RADIUS) continue;
            
            selectedObject = new GameObject(go.id, go.x, go.y, go.col, go.row);
            mouseDragging = true;
            mouseOffsetX = mouseX - go.x;
            mouseOffsetY = mouseY - go.y;
            break;
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging && selectedObject != null) {
            // actualizamos la posicion de la ficha al arrastrar
            double objX = event.getX() - mouseOffsetX;
            double objY = event.getY() - mouseOffsetY;

            selectedObject.x = (int)objX;
            selectedObject.y = (int)objY;
        }
        setOnMouseMoved(event);
    }

    private void onMouseReleased(MouseEvent event) {
        if (selectedObject != null && mouseDragging) {
            double mouseX = event.getX();
            double mouseY = event.getY();

            // comprobamos que aun es el turno del usuario y que ha soltado la ficha dentro del tablero
            if (Main.isMyTurn && grid.isPositionInsideGrid(mouseX, mouseY)) {
                int col = grid.getCol(mouseX);
                
                // enviamos la jugada al servidor con la columna seleccionada y el ID de la ficha
                JSONObject msg = new JSONObject();
                msg.put("type", "clientPlay");
                msg.put("column", col);
                msg.put("pieceId", selectedObject.id);
                
                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(msg.toString());
                }
            }

            mouseDragging = false;
            selectedObject = null;
        }
    }

    // Snap piece so its left-top corner sits exactly on the grid cell under its left tip.
    // esto ya no se usa pero lo dejo por si acaso
    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;

        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;

        return positionX >= objectLeftX && positionX < objectRightX &&
               positionY >= objectTopY && positionY < objectBottomY;
    }

    /*
     * 
     * VICTOR REVISA ESTO QUE LA ANIMACION METE UNOS PARPADEOS QUE NO VEAS
     * 
     */
    
    // empezar la animacion de caida de una ficha
    public void startFallingAnimation(String pieceId, double startY, double targetY) {
        // si ya se animo antes, no la animo otra vez
        if (animatedPieces.contains(pieceId)) return;
        
        // si ya se esta animando, no hacer nada
        if (fallingPieces.containsKey(pieceId)) return;
        
        // solo animar si la ficha baja
        if (targetY <= startY) return;
        
        // marcar como animada y empezar
        animatedPieces.add(pieceId);
        fallingPieces.put(pieceId, new FallingPiece(pieceId, startY, targetY));
    }

    // actualizar el juego y las animaciones
    private void run(double fps) {
        if (animationTimer.fps < 1) return;

        // actualizar las fichas que estan cayendo
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, FallingPiece> entry : fallingPieces.entrySet()) {
            FallingPiece falling = entry.getValue();
            
            // aplicar gravedad suave
            falling.velocity += 0.8; // gravedad mas suave
            falling.currentY += falling.velocity;
            
            // ver si ya llego abajo
            if (falling.currentY >= falling.targetY) {
                falling.currentY = falling.targetY;
                toRemove.add(entry.getKey());
            }
        }
        
        // quitar las animaciones que terminaron
        for (String id : toRemove) {
            fallingPieces.remove(id);
        }
    }

    // dibujar todo en el canvas
    public void draw() {
        if (Main.clients == null) return;

        // limpiar todo
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // dibujar el tablero
        drawGrid();

        // dibujar todas las fichas
        drawAllPieces();
        
        // dibujar la ficha que estoy arrastrando encima de todo
        if (mouseDragging && selectedObject != null) {
            drawDraggedPiece(selectedObject);
        }
        
        // dibujar el cursor del otro jugador
        drawClientPointers();

        // mostrar FPS si hace falta
        if (showFPS) {
            animationTimer.drawFPS(gc);
        }
    }

    public void drawGrid() {
        double cellSize = grid.getCellSize();
        
        // dibujar el fondo azul del tablero
        gc.setFill(Color.BLUE);
        gc.fillRect(grid.getStartX(), grid.getStartY(), 
                    grid.getCols() * cellSize, grid.getRows() * cellSize);

        // dibujar los circulos blancos (agujeros para las fichas)
        gc.setFill(Color.WHITE);
        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double x = grid.getStartX() + col * cellSize + cellSize / 2;
                double y = grid.getStartY() + row * cellSize + cellSize / 2;
                double radius = cellSize * 0.4;
                gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }
    }

    private void drawAllPieces() {
        if (Main.objects == null) return;
        
        // obtenemos el rol del usuario actual para identificar sus fichas
        String myRole = "";
        if (Main.clients != null) {
            myRole = Main.clients.stream()
                .filter(c -> c.name.equals(Main.clientName))
                .map(c -> c.role)
                .findFirst()
                .orElse("");
        }
        
        // renderizamos todas las fichas del juego
        for (GameObject piece : Main.objects) {
            if (piece.id == null) continue;
            
            // la ficha que esta siendo arrastrada se dibuja al final para que aparezca encima
            if (mouseDragging && selectedObject != null && piece.id.equals(selectedObject.id)) continue;
            
            // determinamos el color de la ficha segun su ID
            Color pieceColor;
            if (piece.id.startsWith("R_")) {
                pieceColor = Color.RED;
            } else if (piece.id.startsWith("Y_")) {
                pieceColor = Color.YELLOW;
            } else {
                pieceColor = Color.GRAY;
            }
            
            // aplicamos opacidad a las fichas que el usuario no puede mover actualmente
            boolean isMyPiece = piece.role != null && piece.role.equals(myRole);
            if (!Main.isMyTurn || !isMyPiece) {
                pieceColor = pieceColor.deriveColor(0, 1, 1, 0.5);
            }
            
            // si la ficha esta cayendo, usamos la posicion de la animacion
            double drawY = piece.y;
            if (fallingPieces.containsKey(piece.id)) {
                drawY = fallingPieces.get(piece.id).currentY;
            }
            
            // dibujamos el circulo de la ficha con su borde
            gc.setFill(pieceColor);
            gc.fillOval(piece.x - PIECE_RADIUS, drawY - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(piece.x - PIECE_RADIUS, drawY - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
        }
    }
    
    /**
     * Draw hitboxes around all pieces for debugging
     */
    private void drawHitboxes() {
        if (Main.objects == null || Main.clients == null) return;
        
        // Get my role
        String myRole = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.role)
            .findFirst()
            .orElse("");
        
        for (GameObject piece : Main.objects) {
            if (piece.id == null) continue;
            
            // Use animated Y position if piece is falling
            double drawY = piece.y;
            if (fallingPieces.containsKey(piece.id)) {
                drawY = fallingPieces.get(piece.id).currentY;
            }
            
            // Draw hitbox circle
            // Green for my pieces, gray for opponent's pieces
            if (!myRole.isEmpty() && piece.id.startsWith(myRole + "_")) {
                gc.setStroke(Color.LIME);
                gc.setLineWidth(2);
            } else {
                gc.setStroke(Color.GRAY);
                gc.setLineWidth(1);
            }
            gc.strokeOval(piece.x - PIECE_RADIUS, drawY - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
            
            // Draw center point
            gc.setFill(Color.WHITE);
            gc.fillOval(piece.x - 2, drawY - 2, 4, 4);
        }
    }
    
    // dibujar el cursor del contrincante
    private void drawClientPointers() {
        if (Main.clients == null) return;
        
        for (ClientData client : Main.clients) {
            // no dibujar mi propio cursor
            if (client.name.equals(Main.clientName)) continue;
            
            // ver el color segun el rol
            Color pointerColor;
            if (client.role != null) {
                if (client.role.equals("R")) {
                    pointerColor = Color.RED;
                } else {
                    pointerColor = Color.YELLOW;
                }
            } else {
                pointerColor = Color.GRAY;
            }
            
            // dibujar circulo semitransparente donde esta el raton del otro
            double x = client.mouseX;
            double y = client.mouseY;
            double radius = 15;
            
            // hacer el color transparente
            gc.setFill(Color.color(
                pointerColor.getRed(), 
                pointerColor.getGreen(), 
                pointerColor.getBlue(), 
                0.3
            ));
            
            // dibujar el circulo
            gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }
    }

    private void drawPlayerPieces() {
        if (Main.objects == null || Main.clients == null) return;
        
        // Get local player's role
        String myRole = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.role)
            .findFirst()
            .orElse("");
        
        if (myRole.isEmpty()) return;

        // Filter pieces of the local player
        List<GameObject> myPieces = new ArrayList<>();
        for (GameObject go : Main.objects) {
            if (go.id != null && go.id.startsWith(myRole + "_")) {
                myPieces.add(go);
            }
        }

        // Draw pieces on the right side
        double startX = canvas.getWidth() - PIECES_START_X_OFFSET;
        double startY = grid.getStartY() + 10;
        
        for (int i = 0; i < myPieces.size(); i++) {
            double x = startX + PIECE_RADIUS;
            double y = startY + i * (2 * PIECE_RADIUS + PIECE_MARGIN) + PIECE_RADIUS;
            
            Color pieceColor = myRole.equals("R") ? Color.RED : Color.YELLOW;
            gc.setFill(pieceColor);
            gc.fillOval(x - PIECE_RADIUS, y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(1.5);
            gc.strokeOval(x - PIECE_RADIUS, y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
        }
    }

    private GameObject getPieceAtPosition(double mouseX, double mouseY) {
        if (Main.objects == null || Main.clients == null) return null;
        
        // buscar mis datos de cliente
        ClientData myClient = null;
        for (ClientData c : Main.clients) {
            if (c.name.equals(Main.clientName)) {
                myClient = c;
                break;
            }
        }
        
        if (myClient == null) return null;
        
        String myRole = myClient.role;
        if (myRole == null || myRole.isEmpty()) return null;

        // buscar fichas de mi color que NO esten en el tablero (x > 400)
        for (GameObject piece : Main.objects) {
            if (piece.id == null || !piece.id.startsWith(myRole + "_")) continue;
            
            // solo puedo coger fichas que no esten ya en el tablero
            if (piece.x <= 400) continue;
            
            // ver si he clickado dentro de la ficha
            double distance = Math.sqrt(Math.pow(mouseX - piece.x, 2) + Math.pow(mouseY - piece.y, 2));
            
            if (distance <= PIECE_RADIUS) {
                return piece;
            }
        }
        
        return null;
    }

    private void drawDraggedPiece(GameObject piece) {
        if (piece == null || Main.clients == null) return;
        
        // buscar mi rol para saber el color
        String myRole = "";
        for (ClientData c : Main.clients) {
            if (c.name.equals(Main.clientName)) {
                myRole = c.role;
                break;
            }
        }
        
        Color pieceColor;
        if (myRole.equals("R")) {
            pieceColor = Color.RED;
        } else {
            pieceColor = Color.YELLOW;
        }
        
        // dibujar la ficha
        gc.setFill(pieceColor);
        gc.fillOval(piece.x - PIECE_RADIUS, piece.y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeOval(piece.x - PIECE_RADIUS, piece.y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
    }

    public void drawObject(GameObject obj) {
        double cellSize = grid.getCellSize();

        int x = obj.x;
        int y = obj.y;
        double width = obj.col * cellSize;
        double height = obj.row * cellSize;

        // color del objeto
        Color color = Color.GRAY;

        // dibujar el rectangulo
        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        // dibujar el contorno
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        // poner texto con el id
        gc.setFill(Color.BLACK);
        gc.fillText(obj.id, x + 5, y + 15);
    }

    // funcion auxiliar para conseguir colores
    public Color getColor(String colorName) {
        if (colorName.equals("red")) {
            return Color.RED;
        } else if (colorName.equals("blue")) {
            return Color.BLUE;
        } else if (colorName.equals("green")) {
            return Color.GREEN;
        } else if (colorName.equals("yellow")) {
            return Color.YELLOW;
        } else if (colorName.equals("orange")) {
            return Color.ORANGE;
        } else if (colorName.equals("purple")) {
            return Color.PURPLE;
        } else if (colorName.equals("pink")) {
            return Color.PINK;
        } else if (colorName.equals("brown")) {
            return Color.BROWN;
        } else if (colorName.equals("gray")) {
            return Color.GRAY;
        } else if (colorName.equals("black")) {
            return Color.BLACK;
        } else {
            return Color.LIGHTGRAY;
        }
    }
}
