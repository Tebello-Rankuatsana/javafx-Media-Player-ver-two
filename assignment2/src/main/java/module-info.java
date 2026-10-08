module ranks {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    opens ranks to javafx.fxml;
    exports ranks;
}
