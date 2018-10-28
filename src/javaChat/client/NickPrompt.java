package javaChat.client;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;


class NickPrompt {
    // ник 3-8 символов [латинские буквы, цифры и "_"] без пробелов (!)
    private static final String NICK_PATTERN = "[A-Za-z0-9_]{3,8}";
    private static final String TEXT_PROMPT = "3-8 characters [letters, numbers or _]";
    private String newNick = "";

    NickPrompt(Window owner) {
        final Stage dialog = new Stage();

        dialog.setTitle("Enter New Nick:");
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initModality(Modality.WINDOW_MODAL);

        final TextField textField = new TextField();
        final Button cancelButton = new Button("Cancel");
        final Button submitButton = new Button("Submit");
        submitButton.setMinWidth(100);
        cancelButton.setMinWidth(100);
        submitButton.setDefaultButton(true);
        textField.setMinHeight(TextField.USE_PREF_SIZE);
        textField.setPromptText(TEXT_PROMPT);
        textField.setFocusTraversable(false);

        cancelButton.setOnAction(t -> {
            newNick = "";
            dialog.close();
        });
        submitButton.setOnAction(t -> {
            String nick = textField.getText().trim();
            System.out.println("nick = "+nick);
            if (validNick(nick)) {
                System.out.println("Valid nick!");
                newNick = nick;
                dialog.close();
            }
        });

        final VBox layout = new VBox(10);
        final HBox layout1 = new HBox(10);
        layout1.setAlignment(Pos.CENTER_RIGHT);
        layout.setStyle("-fx-background-color: azure; -fx-padding: 10;");
        layout1.getChildren().setAll( cancelButton, submitButton);
        layout.getChildren().setAll(textField, layout1);

        dialog.setScene(new Scene(layout));
        dialog.showAndWait();
    }

    private boolean validNick(String nick) {
        return nick.matches(NICK_PATTERN);
    }

    String getNewNick() {
        return newNick;
    }
}
