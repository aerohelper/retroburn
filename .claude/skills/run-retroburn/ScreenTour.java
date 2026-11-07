// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

import io.aerohelper.retroburn.app.AppContext;
import io.aerohelper.retroburn.app.MainWindow;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Drives the real app for a look: starts it on this machine's platform, then runs the steps given
 * on the command line and closes it. Not part of the build.
 *
 * <p>Usage: {@code ScreenTour <out-dir> <step>...} where a step is {@code card:<title>} (press a
 * home-screen card), {@code button:<text>} (press a button by its text), {@code wait:<ms>} or
 * {@code snap:<name>} (save {@code <out-dir>/<name>.png} and print the visible labels).
 */
public final class ScreenTour extends Application {

    /** Plain entry point, so the java launcher does not insist on JavaFX modules. */
    public static final class Main {
        public static void main(String[] args) {
            Application.launch(ScreenTour.class, args);
        }
    }

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        List<String> args = getParameters().getRaw();
        File out = new File(args.get(0));
        out.mkdirs();
        AppContext context = AppContext.createDefault();
        new MainWindow(context).show(stage);
        context.checkToolsInBackground();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(2000);
                for (String step : args.subList(1, args.size())) {
                    System.out.println("step " + step);
                    String value = step.substring(step.indexOf(':') + 1);
                    switch (step.substring(0, step.indexOf(':'))) {
                        case "card" -> fx(() -> clickCard(value));
                        case "button" -> fx(() -> clickButton(value));
                        case "menu" -> fx(() -> clickMenu(value));
                        case "wait" -> Thread.sleep(Long.parseLong(value));
                        case "snap" -> fx(() -> snap(out, value));
                        default -> throw new IllegalArgumentException("Unknown step " + step);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    stage.close();
                    context.close();
                    Platform.exit();
                });
            }
        });
    }

    private void clickCard(String title) {
        for (Node node : stage.getScene().getRoot().lookupAll(".project-card")) {
            Button card = (Button) node;
            if (card.getAccessibleText().startsWith(title + ".")) {
                System.out.println("  card " + title + " disabled=" + card.isDisabled());
                card.fire();
                return;
            }
        }
        throw new IllegalStateException("No card " + title);
    }

    private void clickButton(String text) {
        for (Node node : stage.getScene().getRoot().lookupAll(".button")) {
            if (node instanceof Button button && text.equals(button.getText())) {
                System.out.println("  button " + text + " disabled=" + button.isDisabled());
                button.fire();
                return;
            }
        }
        throw new IllegalStateException("No button " + text);
    }

    /** Chooses a menu item by its path of texts, such as {@code View>Theme>Y2K}, as a click would. */
    private void clickMenu(String path) {
        String[] names = path.split(">");
        MenuBar bar = (MenuBar) stage.getScene().getRoot().lookup(".menu-bar");
        List<MenuItem> items = new java.util.ArrayList<>(bar.getMenus());
        for (int level = 0; level < names.length; level++) {
            String name = names[level];
            MenuItem found = items.stream()
                    .filter(item -> name.equals(item.getText()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No menu item " + name + " in " + path));
            if (level < names.length - 1) {
                items = ((Menu) found).getItems();
                continue;
            }
            // A click toggles check and radio items before their action runs; fire() alone does not.
            if (found instanceof RadioMenuItem radio) {
                radio.setSelected(true);
            } else if (found instanceof CheckMenuItem check) {
                check.setSelected(!check.isSelected());
            }
            System.out.println("  menu " + path + " disabled=" + found.isDisable());
            found.fire();
        }
    }

    private void snap(File out, String name) {
        for (Node node : stage.getScene().getRoot().lookupAll(".label")) {
            String text = ((Label) node).getText();
            if (node.isVisible() && text != null && !text.isBlank()) {
                System.out.println("  [" + name + "] " + text);
            }
        }
        WritableImage image = stage.getScene().snapshot(null);
        PixelReader reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        try {
            ImageIO.write(buffered, "png", new File(out, name + ".png"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fx(Runnable action) throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get();
    }
}
