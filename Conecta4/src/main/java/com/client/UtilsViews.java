package com.client;

import java.util.ArrayList;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class UtilsViews {

    public static StackPane parentContainer = new StackPane();
    public static ArrayList<Object> controllers = new ArrayList<>();

    // Agregar una vista a la lista
    public static void addView(Class<?> cls, String name, String path) throws Exception {
        
        boolean defaultView = false;
        FXMLLoader loader = new FXMLLoader(cls.getResource(path));
        Pane view = loader.load();
        ObservableList<Node> children = parentContainer.getChildren();

        // La primera vista es la vista por defecto
        if (children.isEmpty()) {
            defaultView = true;
        }

        view.setId(name);
        view.setVisible(defaultView);
        view.setManaged(defaultView);

        children.add(view);
        controllers.add(loader.getController());
    }

    // Obtener controlador por id de vista (viewId)
    public static Object getController(String viewId) {
        int index = 0;
        for (Node n : parentContainer.getChildren()) {
            if (n.getId() != null && n.getId().equals(viewId)) {
                return controllers.get(index);
            }
            index++;
        }
        return null;
    }

    // Obtener nombre de la vista activa
    public static String getActiveView() {
        for (Node n : parentContainer.getChildren()) {
            if (n.isVisible() && n.getId() != null) {
                return n.getId();
            }
        }
        return null; // No hay ninguna vista activa
    }

    // Establecer vista visible por su id (viewId)
    public static void setView(String viewId) {

        ArrayList<Node> list = new ArrayList<>();
        list.addAll(parentContainer.getChildrenUnmodifiable());

        // Mostrar siguiente vista, ocultar otras
        for (Node n : list) {
            if (n.getId() != null && n.getId().equals(viewId)) {
                n.setVisible(true);
                n.setManaged(true);
            } else {
                n.setVisible(false);
                n.setManaged(false);
            }
        }

        // Quitar foco de los botones
        parentContainer.requestFocus();
    }

    // Establecer vista visible por su id (viewId) con animacion
    public static void setViewAnimating(String viewId) {

        ArrayList<Node> list = new ArrayList<>();
        list.addAll(parentContainer.getChildrenUnmodifiable());

        // Obtener vista actual
        Node curView = null;
        for (Node n : list) {
            if (n.isVisible()) {
                curView = n;
            }
        }

        // verificar si hay una vista actual antes de comparar
        if (curView != null && curView.getId() != null && curView.getId().equals(viewId)) {
            return; // No hacer nada si la vista actual es la misma que la siguiente
        }

        // Obtener siguiente vista
        Node nxtView = null;
        for (Node n : list) {
            if (n.getId().equals(viewId)) {
                nxtView = n;
            }
        }

        // verificar que la siguiente vista existe
        if (nxtView == null) {
            System.err.println("Error: View with id '" + viewId + "' not found");
            return;
        }

        // Establecer siguiente vista como visible
        nxtView.setVisible(true);
        nxtView.setManaged(true);

        // si no hay vista actual, simplemente mostrar la nueva sin animacion
        if (curView == null) {
            // ocultar todas las demas vistas
            for (Node n : list) {
                if (!n.getId().equals(viewId)) {
                    n.setVisible(false);
                    n.setManaged(false);
                }
            }
            parentContainer.requestFocus();
            return;
        }

        // Por defecto, establecer animacion hacia la izquierda
        double width = parentContainer.getScene().getWidth();
        double xLeftStart = 0;
        double xLeftEnd = 0;
        double xRightStart = 0;
        double xRightEnd = 0;
        Node animatedViewLeft = null;
        Node animatedViewRight = null;

        if (list.indexOf(curView) < list.indexOf(nxtView)) {

            // If curView is lower than nxtView, animate to the left
            xLeftStart = 0;
            xLeftEnd = -width;
            xRightStart = width;
            xRightEnd = 0;
            animatedViewLeft = curView;
            animatedViewRight = nxtView;

            curView.translateXProperty().set(xLeftStart);
            nxtView.translateXProperty().set(xRightStart);

        } else { 

            // If curView is greater than nxtView, animate to the right
            xLeftStart = -width;
            xLeftEnd = 0;
            xRightStart = 0;
            xRightEnd = width;
            animatedViewLeft = nxtView;
            animatedViewRight = curView;

            curView.translateXProperty().set(xRightStart);
            nxtView.translateXProperty().set(xLeftStart);
        }

        // Animate leftView 
        final double seconds = 0.4;
        KeyValue kvLeft = new KeyValue(animatedViewLeft.translateXProperty(), xLeftEnd, Interpolator.EASE_BOTH);
        KeyFrame kfLeft = new KeyFrame(Duration.seconds(seconds), kvLeft);
        Timeline timelineLeft = new Timeline();
        timelineLeft.getKeyFrames().add(kfLeft);
        timelineLeft.play();

        // Animate rightView 
        KeyValue kvRight = new KeyValue(animatedViewRight.translateXProperty(), xRightEnd, Interpolator.EASE_BOTH);
        KeyFrame kfRight = new KeyFrame(Duration.seconds(seconds), kvRight);
        Timeline timelineRight = new Timeline();
        timelineRight.getKeyFrames().add(kfRight);
        timelineRight.setOnFinished(t -> {
            // Hide other views and reset all translations
            for (Node n : list) {
                if (!n.getId().equals(viewId)) {
                    n.setVisible(false);
                    n.setManaged(false);
                }
                n.translateXProperty().set(0);
            }
        });
        timelineRight.play();

        // Remove focus from buttons
        parentContainer.requestFocus();
    }
}
