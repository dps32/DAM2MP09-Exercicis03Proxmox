package com.client;

import java.net.URL;
import java.util.ResourceBundle;

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

    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    private GameObject selectedObject = null;

    private final double PIECE_RADIUS = 20;
    private final double PIECE_MARGIN = 10;
    private final double PIECES_START_X_OFFSET = 80;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // tablero 7 columnas x 6 filas
        grid = new PlayGrid(25, 25, 50, 6, 7);
        System.out.println("[DEBUG] Grid inicializado: " + grid.getCols() + "x" + grid.getRows());

        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Start animation timer
    public void start() {
        animationTimer.start();
    }

    // Stop animation timer
    public void stop() {
        animationTimer.stop();
    }

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        String color = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.color)
            .findFirst()
            .orElse("gray");

        ClientData cd = new ClientData(
            Main.clientName, 
            color,
            (int)mouseX, 
            (int)mouseY,  
            grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getRow(mouseY) : -1,
            grid.isPositionInsideGrid(mouseX, mouseY) ? grid.getCol(mouseX) : -1
        );

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

        // First check if clicked on available piece
        GameObject pieceClicked = getPieceAtPosition(mouseX, mouseY);
        if (pieceClicked != null) {
            selectedObject = new GameObject(pieceClicked.id, (int)mouseX, (int)mouseY, pieceClicked.col, pieceClicked.row);
            mouseDragging = true;
            mouseOffsetX = 0;
            mouseOffsetY = 0;
            return;
        }

        // Check if clicked on existing objects on board
        for (GameObject go : Main.objects) {
            if (isPositionInsideObject(mouseX, mouseY, go.x, go.y, go.col, go.row)) {
                selectedObject = new GameObject(go.id, go.x, go.y, go.col, go.row);
                mouseDragging = true;
                mouseOffsetX = event.getX() - go.x;
                mouseOffsetY = event.getY() - go.y;
                break;
            }
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            double objX = event.getX() - mouseOffsetX;
            double objY = event.getY() - mouseOffsetY;

            selectedObject = new GameObject(selectedObject.id, (int)objX, (int)objY, (int)selectedObject.col, (int)selectedObject.row);

            JSONObject msg = new JSONObject();
            msg.put("type", "clientObjectMoving");
            msg.put("value", selectedObject.toJSON());

            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msg.toString());
            }
        }
        setOnMouseMoved(event);
    }

    private void onMouseReleased(MouseEvent event) {
        if (selectedObject != null && mouseDragging) {
            double mouseX = event.getX();
            double mouseY = event.getY();

            // Check if released over the grid
            if (grid.isPositionInsideGrid(mouseX, mouseY)) {
                int col = grid.getCol(mouseX);
                
                // Send play to server
                JSONObject msg = new JSONObject();
                msg.put("type", "clientPlay");
                msg.put("column", col);
                
                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(msg.toString());
                    System.out.println("[DEBUG] Play sent: column " + col);
                }
            }

            mouseDragging = false;
            selectedObject = null;
        }
    }

    // Snap piece so its left-top corner sits exactly on the grid cell under its left tip.
    private void snapObjectLeftTop(GameObject obj) {
        int col = grid.getCol(obj.x); // left X -> column
        int row = grid.getRow(obj.y); // top Y  -> row

        // clamp inside grid
        col = (int) Math.max(0, Math.min(col, grid.getCols() - 1));
        row = (int) Math.max(0, Math.min(row, grid.getRows() - 1));

        obj.x = grid.getCellX(col);
        obj.y = grid.getCellY(row);
    }

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

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) { return; }

        // Update objects and animations here
    }

    // Draw game to canvas
    public void draw() {

        if (Main.clients == null) { return; }

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw grid
        drawGrid();

        // Draw player pieces on the right
        drawPlayerPieces();

        // Draw dragged piece if any
        if (mouseDragging && selectedObject != null) {
            drawDraggedPiece(selectedObject);
        }

        // Draw FPS if needed
        if (showFPS) { animationTimer.drawFPS(gc); }   
    }

    public void drawGrid() {
        double cellSize = grid.getCellSize();
        
        // Draw board background
        gc.setFill(Color.BLUE);
        gc.fillRect(grid.getStartX(), grid.getStartY(), 
                    grid.getCols() * cellSize, grid.getRows() * cellSize);

        // Draw empty circles (holes for pieces)
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
        java.util.List<GameObject> myPieces = new java.util.ArrayList<>();
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
        
        String myRole = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.role)
            .findFirst()
            .orElse("");
        
        if (myRole.isEmpty()) return null;

        java.util.List<GameObject> myPieces = new java.util.ArrayList<>();
        for (GameObject go : Main.objects) {
            if (go.id != null && go.id.startsWith(myRole + "_")) {
                myPieces.add(go);
            }
        }

        double startX = canvas.getWidth() - PIECES_START_X_OFFSET;
        double startY = grid.getStartY() + 10;
        
        for (int i = 0; i < myPieces.size(); i++) {
            double x = startX + PIECE_RADIUS;
            double y = startY + i * (2 * PIECE_RADIUS + PIECE_MARGIN) + PIECE_RADIUS;
            
            double distance = Math.sqrt(Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2));
            if (distance <= PIECE_RADIUS) {
                return myPieces.get(i);
            }
        }
        
        return null;
    }

    private void drawDraggedPiece(GameObject piece) {
        if (piece == null || Main.clients == null) return;
        
        String myRole = Main.clients.stream()
            .filter(c -> c.name.equals(Main.clientName))
            .map(c -> c.role)
            .findFirst()
            .orElse("");
        
        Color pieceColor = myRole.equals("R") ? Color.RED : Color.YELLOW;
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

        // Seleccionar un color basat en l'objectId
        Color color = Color.GRAY;

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        // Dibuixar el contorn
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        // Opcionalment, afegir text (per exemple, l'objectId)
        gc.setFill(Color.BLACK);
        gc.fillText(obj.id, x + 5, y + 15);
    }

    public Color getColor(String colorName) {
        switch (colorName.toLowerCase()) {
            case "red":
                return Color.RED;
            case "blue":
                return Color.BLUE;
            case "green":
                return Color.GREEN;
            case "yellow":
                return Color.YELLOW;
            case "orange":
                return Color.ORANGE;
            case "purple":
                return Color.PURPLE;
            case "pink":
                return Color.PINK;
            case "brown":
                return Color.BROWN;
            case "gray":
                return Color.GRAY;
            case "black":
                return Color.BLACK;
            default:
                return Color.LIGHTGRAY; // Default color
        }
    }
}
